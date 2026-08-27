package io.github.eat_ram.fuream.hook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.FurnaceCompat;
import io.github.eat_ram.fuream.compat.ComparatorCompat;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.data.FurnaceFureamDataRegistry;
import io.github.eat_ram.fuream.logic.FureamFurnaceEngine;
import io.github.eat_ram.fuream.logic.FuelTable;
import io.github.eat_ram.fuream.logic.PassiveLaneProjection;
import io.github.eat_ram.fuream.logic.PassiveLaneProjection.SelectionMode;
import io.github.eat_ram.fuream.nbt.FurnaceRootNbtBridge;
import io.github.eat_ram.fuream.nbt.NativeNbtCompound;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.util.FurnacePos;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FurnaceManager {
    public static final Map<FurnacePos, FurnaceContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final String PDC_ROOT_KEY = "PublicBukkitValues";
    private static long tickCounter = 0;

    public static class FurnaceContext implements FureamDataHolder {
        public final @NotNull FurnacePos pos;
        public final @NotNull FurnaceType type;
        public final @NotNull FureamFurnaceData data;
        public final @NotNull Map<@NotNull String, @NotNull FureamData> extraData = new HashMap<>();

        public int burnTime;
        public int fuelTimeTotal;
        public int cookTime;
        public int cookTimeTotal;

        public final @NotNull Map<UUID, FureamScreenInventory> sessions = new ConcurrentHashMap<>();
        public boolean dirty;
        public boolean passiveProjected;
        public final @NotNull PassiveLaneProjection.State passiveInput =
            new PassiveLaneProjection.State();
        public final @NotNull PassiveLaneProjection.State passiveFuel =
            new PassiveLaneProjection.State();
        public final @NotNull PassiveLaneProjection.State passiveOutput =
            new PassiveLaneProjection.State();
        public int lastInputSlot = -1;
        public @Nullable KeyableItemStack lastInputKey;
        public boolean startSmeltPending = true;
        public int lastComparatorSignal = -1;

        public FurnaceContext(@NotNull FurnacePos pos, @NotNull FurnaceType type) {
            this.pos = pos;
            this.type = type;
            this.data = new FureamFurnaceData();
            this.cookTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
            this.fuelTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
        }

        public @Nullable World getWorld() {
            return Bukkit.getWorld(this.pos.worldName);
        }

        @Override
        public <T extends FureamData> @Nullable T getFureamData(String key, Class<T> clazz) {
            if ("fuream".equals(key)) {
                if (clazz.isInstance(this.data)) {
                    return clazz.cast(this.data);
                }
            }
            FureamData extra = this.extraData.get(key);
            if (clazz.isInstance(extra)) {
                return clazz.cast(extra);
            }
            return null;
        }
    }

    public static @Nullable FurnaceContext getOrCreateContext(@NotNull Block block) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return null;

        FurnacePos pos = new FurnacePos(block);
        FurnaceContext existing = CONTEXTS.get(pos);
        if (existing != null) {
            return existing;
        }

        FurnaceContext ctx = new FurnaceContext(pos, type);
        for (Map.Entry<String, java.util.function.Function<? super FureamDataHolder, ? extends FureamData>> entry :
             FurnaceFureamDataRegistry.REGISTRY.entrySet()) {
            if (!"fuream".equals(entry.getKey())) {
                ctx.extraData.put(entry.getKey(), entry.getValue().apply(ctx));
            }
        }

        BlockState state = block.getState();
        if (state instanceof Furnace) {
            try {
                boolean nativeLoaded = FurnaceRootNbtBridge.loadNativeData(state, ctx);
                NativeNbtCompound root = FurnaceRootNbtBridge.capturedRoot(state);
                NativeNbtCompound legacyNbt = getLegacyPdc(root);
                boolean hasLegacyData = hasFureamStorage(legacyNbt);
                NativeNbtCompound dataCompound = nativeLoaded ? null : getDataCompound(legacyNbt);
                boolean loaded = nativeLoaded;
                if (dataCompound != null) {
                    ctx.data.readNbt(ctx, dataCompound);
                    for (Map.Entry<String, FureamData> entry : ctx.extraData.entrySet()) {
                        entry.getValue().readNbt(ctx, dataCompound);
                    }
                    ctx.burnTime = legacyNbt.getInt("BurnTime", ctx.burnTime);
                    ctx.cookTime = legacyNbt.getInt("CookTime", ctx.cookTime);
                    ctx.cookTimeTotal = legacyNbt.getInt("CookTimeTotal", ctx.cookTimeTotal);
                    loaded = true;
                }

                boolean enabled = isEnabled(block.getWorld(), type);
                if (!loaded && !enabled) {
                    return null;
                }
                if (!loaded) {
                    migrateVanillaLane(ctx, (Furnace) state);
                } else if (enabled) {
                    clearNativeLane((Furnace) state);
                } else {
                    projectPassiveLane(ctx, (Furnace) state);
                }
                if (hasLegacyData) {
                    removeLegacyPdc(legacyNbt);
                    ctx.dirty = true;
                }
                initializeInputIdentity(ctx);
            } catch (Exception e) {
                FureamMain.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Unable to load furnace data at " + pos, e);
                return null;
            }
        }

        CONTEXTS.put(pos, ctx);
        if (ctx.dirty) {
            saveToNbt(ctx);
        }
        return ctx;
    }

    private static void initializeInputIdentity(@NotNull FurnaceContext ctx) {
        int inputSlot = firstPresentSlot(ctx.data.inputs);
        ctx.lastInputSlot = inputSlot;
        if (inputSlot < 0) {
            ctx.lastInputKey = null;
            ctx.startSmeltPending = true;
            return;
        }

        ItemStack input = ctx.data.inputs.get(inputSlot).clone();
        input.setAmount(1);
        ctx.lastInputKey = new KeyableItemStack(input);
        ctx.startSmeltPending = ctx.data.runningRecipe == null || ctx.cookTime <= 0;
    }

    public static void saveToNbt(@NotNull FurnaceContext ctx) {
        World world = ctx.getWorld();
        if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) return;

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        BlockState state = block.getState();
        if (state instanceof Furnace) {
            try {
                if (!ctx.passiveProjected) {
                    absorbVanillaLane(ctx, (Furnace) state);
                } else {
                    syncPassiveLane(ctx, (Furnace) state);
                }
                FurnaceRootNbtBridge.store(state, ctx);
                ctx.dirty = false;
            } catch (Exception e) {
                FureamMain.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                    "Unable to save furnace data at " + ctx.pos, e);
            }
        }
    }

    private static void absorbVanillaLane(
        @NotNull FurnaceContext ctx, @NotNull Furnace furnaceState
    ) {
        FurnaceInventory inv = furnaceState.getInventory();
        inv.setSmelting(absorbLaneStack(ctx.data.inputs, inv.getSmelting()));
        inv.setFuel(absorbLaneStack(ctx.data.fuels, inv.getFuel()));
        inv.setResult(absorbLaneStack(ctx.data.outputs, inv.getResult()));
    }

    private static @Nullable ItemStack absorbLaneStack(
        @NotNull java.util.List<ItemStack> target, @Nullable ItemStack stack
    ) {
        if (ItemCompat.isEmpty(stack)) {
            return null;
        }
        int maxSlots = Math.max(1, target.size());
        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(target, stack, maxSlots);
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static void migrateVanillaLane(
        @NotNull FurnaceContext ctx, @NotNull Furnace furnaceState
    ) {
        FurnaceInventory inv = furnaceState.getInventory();
        ItemStack smelting = inv.getSmelting();
        ItemStack fuel = inv.getFuel();
        ItemStack result = inv.getResult();

        if (!ItemCompat.isEmpty(smelting)) {
            ctx.data.inputs.set(0, smelting.clone());
        }
        if (!ItemCompat.isEmpty(fuel)) {
            ctx.data.fuels.set(0, fuel.clone());
        }
        if (!ItemCompat.isEmpty(result)) {
            ctx.data.outputs.set(0, result.clone());
        }

        inv.clear();
        ctx.burnTime = FurnaceCompat.getBurnTime(furnaceState);
        ctx.cookTime = FurnaceCompat.getCookTime(furnaceState);
        int nativeTotal = FurnaceCompat.getCookTimeTotal(
            furnaceState, ctx.type == FurnaceType.FURNACE ? 200 : 100
        );
        ctx.cookTimeTotal = nativeTotal > 0
            ? nativeTotal
            : (ctx.type == FurnaceType.FURNACE ? 200 : 100);
        ctx.dirty = true;
    }

    private static @Nullable NativeNbtCompound getDataCompound(@Nullable NativeNbtCompound nbt) {
        if (nbt == null || !nbt.has(FureamMain.DATA_KEY)) {
            return null;
        }
        return nbt.getCompound(FureamMain.DATA_KEY);
    }

    private static @Nullable NativeNbtCompound getLegacyPdc(@Nullable NativeNbtCompound rootNbt) {
        if (rootNbt == null || !rootNbt.has(PDC_ROOT_KEY)) {
            return null;
        }
        return rootNbt.getCompound(PDC_ROOT_KEY);
    }

    private static boolean hasFureamStorage(@Nullable NativeNbtCompound nbt) {
        return nbt != null && (
            nbt.has(FureamMain.DATA_KEY) ||
            nbt.has("BurnTime") ||
            nbt.has("CookTime") ||
            nbt.has("CookTimeTotal")
        );
    }

    private static void removeLegacyPdc(@Nullable NativeNbtCompound legacyNbt) {
        if (legacyNbt == null) return;
        legacyNbt.remove(FureamMain.DATA_KEY);
        legacyNbt.remove("BurnTime");
        legacyNbt.remove("CookTime");
        legacyNbt.remove("CookTimeTotal");
    }

    private static boolean isEnabled(@Nullable World world, @NotNull FurnaceType type) {
        FureamWorldConfig config = FureamMain.getWorldConfig(world);
        return config != null && config.getEnabledFurnaceTypes().contains(type);
    }

    private static void clearNativeLane(@NotNull Furnace furnace) {
        furnace.getInventory().clear();
    }

    private static void projectPassiveLane(@NotNull FurnaceContext ctx, @NotNull Furnace furnace) {
        closeSessions(ctx);
        FurnaceInventory inventory = furnace.getInventory();
        inventory.setSmelting(PassiveLaneProjection.begin(
            ctx.data.inputs, inventory.getSmelting(), SelectionMode.FIRST_PRESENT, ctx.passiveInput
        ));
        inventory.setFuel(PassiveLaneProjection.begin(
            ctx.data.fuels, inventory.getFuel(), SelectionMode.FUEL_THEN_PRESENT, ctx.passiveFuel
        ));
        inventory.setResult(PassiveLaneProjection.begin(
            ctx.data.outputs, inventory.getResult(), SelectionMode.FIRST_PRESENT, ctx.passiveOutput
        ));
        FurnaceCompat.setBurnTime(furnace, Math.max(0, ctx.burnTime));
        FurnaceCompat.setCookTime(furnace, Math.max(0, ctx.cookTime));
        if (ctx.cookTimeTotal > 0) FurnaceCompat.setCookTimeTotal(furnace, ctx.cookTimeTotal);
        ctx.passiveProjected = true;
        ctx.dirty = true;
    }

    private static int firstPresentSlot(@NotNull java.util.List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemCompat.isEmpty(stacks.get(i))) return i;
        }
        return -1;
    }

    private static void resumeActive(@NotNull FurnaceContext ctx, @NotNull Furnace furnace) {
        if (!ctx.passiveProjected) return;
        FurnaceInventory inventory = furnace.getInventory();
        inventory.setSmelting(PassiveLaneProjection.restore(
            ctx.data.inputs, inventory.getSmelting(), ctx.passiveInput
        ));
        inventory.setFuel(PassiveLaneProjection.restore(
            ctx.data.fuels, inventory.getFuel(), ctx.passiveFuel
        ));
        inventory.setResult(PassiveLaneProjection.restore(
            ctx.data.outputs, inventory.getResult(), ctx.passiveOutput
        ));
        ctx.passiveInput.reset();
        ctx.passiveFuel.reset();
        ctx.passiveOutput.reset();
        ctx.burnTime = FurnaceCompat.getBurnTime(furnace);
        ctx.cookTime = FurnaceCompat.getCookTime(furnace);
        ctx.cookTimeTotal = FurnaceCompat.getCookTimeTotal(furnace, ctx.cookTimeTotal);
        ctx.passiveProjected = false;
        ctx.dirty = true;
    }

    private static void syncPassiveLane(@NotNull FurnaceContext ctx, @NotNull Furnace furnace) {
        FurnaceInventory inventory = furnace.getInventory();
        ItemStack input = inventory.getSmelting();
        ItemStack fuel = inventory.getFuel();
        ItemStack output = inventory.getResult();
        boolean changed = false;

        if (ItemCompat.isEmpty(input) && !ctx.passiveInput.isExhausted()) {
            ItemStack next = PassiveLaneProjection.takeNext(
                ctx.data.inputs, SelectionMode.FIRST_PRESENT, ctx.passiveInput
            );
            inventory.setSmelting(next);
            changed = !ItemCompat.isEmpty(next);
        }

        if (ItemCompat.isEmpty(fuel)) {
            if (!ctx.passiveFuel.isExhausted()) {
                ItemStack next = PassiveLaneProjection.takeNext(
                    ctx.data.fuels, SelectionMode.FUEL_THEN_PRESENT, ctx.passiveFuel
                );
                inventory.setFuel(next);
                changed |= !ItemCompat.isEmpty(next);
            }
        } else if (!FuelTable.isFuel(fuel) &&
                   PassiveLaneProjection.hasPreferredFuel(ctx.data.fuels, ctx.passiveFuel)) {
            ItemStack remainder = PassiveLaneProjection.restore(
                ctx.data.fuels, fuel, ctx.passiveFuel
            );
            if (ItemCompat.isEmpty(remainder)) {
                inventory.setFuel(PassiveLaneProjection.takeNext(
                    ctx.data.fuels, SelectionMode.FUEL_THEN_PRESENT, ctx.passiveFuel
                ));
                changed = true;
            } else {
                inventory.setFuel(remainder);
                changed = remainder.getAmount() < fuel.getAmount();
                ctx.passiveFuel.blockPreferredFuelSwitch();
            }
        }

        if (ItemCompat.isEmpty(output) && !ctx.passiveOutput.isExhausted()) {
            ItemStack next = PassiveLaneProjection.takeNext(
                ctx.data.outputs, SelectionMode.FIRST_PRESENT, ctx.passiveOutput
            );
            inventory.setResult(next);
            changed |= !ItemCompat.isEmpty(next);
        }

        if (changed) {
            ctx.dirty = true;
        }
        ctx.burnTime = FurnaceCompat.getBurnTime(furnace);
        ctx.cookTime = FurnaceCompat.getCookTime(furnace);
        ctx.cookTimeTotal = FurnaceCompat.getCookTimeTotal(furnace, ctx.cookTimeTotal);
    }

    public static void tickAll() {
        Iterator<Map.Entry<FurnacePos, FurnaceContext>> it = CONTEXTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FurnacePos, FurnaceContext> entry = it.next();
            FurnaceContext ctx = entry.getValue();
            World world = ctx.getWorld();

            if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) {
                closeSessions(ctx);
                if (ctx.dirty) {
                    saveToNbt(ctx);
                }
                it.remove();
                continue;
            }

            Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
            FurnaceType currentType = FureamMain.getFurnaceType(block);
            if (currentType == null) {
                // Block was removed or changed
                closeSessions(ctx);
                if (ctx.dirty) {
                    saveToNbt(ctx);
                }
                it.remove();
                continue;
            }

            FureamWorldConfig config = FureamMain.getWorldConfig(world);
            boolean enabled = config != null && config.getEnabledFurnaceTypes().contains(ctx.type);
            validateSessions(ctx, block, enabled);
            BlockState state = block.getState();
            if (enabled) {
                if (ctx.passiveProjected && state instanceof Furnace) {
                    resumeActive(ctx, (Furnace) state);
                }
                FureamFurnaceEngine.tick(ctx, config);
                int comparatorSignal = ComparatorCompat.calculate(ctx.data);
                if (comparatorSignal != ctx.lastComparatorSignal || tickCounter % 20 == 0) {
                    ComparatorCompat.updateAround(block, comparatorSignal);
                    ctx.lastComparatorSignal = comparatorSignal;
                }

                // Handle hopper below extraction (every 8 ticks, standard hopper speed)
                if (tickCounter % 8 == 0) {
                    processHopperPull(block, ctx);
                }
            } else if (!ctx.passiveProjected && state instanceof Furnace) {
                projectPassiveLane(ctx, (Furnace) state);
            } else if (ctx.passiveProjected && state instanceof Furnace) {
                syncPassiveLane(ctx, (Furnace) state);
            }

            // Periodic save if dirty
            if (ctx.dirty) {
                if (tickCounter % 40 == 0) {
                    saveToNbt(ctx);
                }
            }
        }
        tickCounter++;
    }

    private static void processHopperPull(@NotNull Block furnaceBlock, @NotNull FurnaceContext ctx) {
        Block below = furnaceBlock.getRelative(BlockFace.DOWN);
        Inventory targetInv = null;

        if (below.getType() == Material.HOPPER) {
            if (isHopperEnabled(below)) {
                BlockState belowState = below.getState();
                if (belowState instanceof Hopper) {
                    targetInv = ((Hopper) belowState).getInventory();
                }
            }
        }

        if (targetInv == null) {
            Location centerBelow = furnaceBlock.getLocation().add(0.5, -0.5, 0.5);
            for (Object entity : nearbyEntities(furnaceBlock.getWorld(), centerBelow)) {
                if (entity instanceof HopperMinecart) {
                    targetInv = ((HopperMinecart) entity).getInventory();
                    break;
                }
            }
        }

        if (targetInv == null) {
            return;
        }

        // Fabric exposes extractable fuel remainders before outputs.
        Material bucket = Material.matchMaterial("BUCKET");
        Material waterBucket = Material.matchMaterial("WATER_BUCKET");
        for (int i = 0; i < ctx.data.fuels.size(); i++) {
            ItemStack fuelStack = ctx.data.fuels.get(i);
            if (!ItemCompat.isEmpty(fuelStack) &&
                (fuelStack.getType() == bucket || fuelStack.getType() == waterBucket)) {
                if (moveOne(furnaceBlock, targetInv, ctx.data.fuels, i, ctx)) return;
            }
        }
        for (int i = 0; i < ctx.data.outputs.size(); i++) {
            if (!ItemCompat.isEmpty(ctx.data.outputs.get(i)) &&
                moveOne(furnaceBlock, targetInv, ctx.data.outputs, i, ctx)) return;
        }
    }

    private static @NotNull Iterable<?> nearbyEntities(
        @NotNull World world, @NotNull Location center
    ) {
        try {
            Object entities = world.getClass().getMethod(
                "getNearbyEntities", Location.class, double.class, double.class, double.class
            ).invoke(world, center, 0.6, 0.6, 0.6);
            if (entities instanceof Iterable) return (Iterable<?>) entities;
        } catch (ReflectiveOperationException ignored) {
        }
        return java.util.Collections.emptyList();
    }

    private static boolean isHopperEnabled(@NotNull Block hopper) {
        try {
            Object data = hopper.getClass().getMethod("getBlockData").invoke(hopper);
            Object result = data.getClass().getMethod("isEnabled").invoke(data);
            return Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException ignored) {
            return !hopper.isBlockPowered();
        }
    }

    private static boolean moveOne(
        @NotNull Block furnaceBlock, @NotNull Inventory target,
        @NotNull java.util.List<ItemStack> sourceSlots, int slot,
        @NotNull FurnaceContext ctx
    ) {
        ItemStack sourceStack = sourceSlots.get(slot);
        if (ItemCompat.isEmpty(sourceStack)) return false;
        ItemStack single = sourceStack.clone();
        single.setAmount(1);
        Inventory source = ((Furnace) furnaceBlock.getState()).getInventory();
        org.bukkit.event.inventory.InventoryMoveItemEvent event =
            new org.bukkit.event.inventory.InventoryMoveItemEvent(source, single, target, false);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || ItemCompat.isEmpty(event.getItem())) return false;
        ItemStack[] targetSnapshot = cloneContents(target.getContents());
        Map<Integer, ItemStack> leftover = target.addItem(event.getItem().clone());
        if (!leftover.isEmpty()) {
            target.setContents(targetSnapshot);
            return false;
        }
        sourceStack.setAmount(sourceStack.getAmount() - 1);
        if (sourceStack.getAmount() <= 0) sourceSlots.set(slot, ItemCompat.empty());
        refreshSessions(ctx);
        ctx.dirty = true;
        return true;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] result = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            result[i] = ItemCompat.isEmpty(contents[i]) ? null : contents[i].clone();
        }
        return result;
    }

    public static void flushAll() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            if (ctx.dirty || ctx.data.hasAny()) {
                saveToNbt(ctx);
            }
        }
    }

    public static void refreshSessions(@NotNull FurnaceContext ctx) {
        for (FureamScreenInventory session : ctx.sessions.values()) {
            session.burnTime = ctx.burnTime;
            session.fuelTimeTotal = Math.max(1, ctx.fuelTimeTotal);
            session.cookTime = ctx.cookTime;
            session.cookTimeTotal = Math.max(1, ctx.cookTimeTotal);
            session.refreshVisuals();
        }
    }

    public static void reconcileConfiguration() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            World world = ctx.getWorld();
            if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) continue;

            Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
            BlockState state = block.getState();
            if (!(state instanceof Furnace) || FureamMain.getFurnaceType(block) != ctx.type) {
                closeSessions(ctx);
                continue;
            }

            boolean enabled = isEnabled(world, ctx.type);
            if (enabled && ctx.passiveProjected) {
                resumeActive(ctx, (Furnace) state);
                initializeInputIdentity(ctx);
            } else if (!enabled && !ctx.passiveProjected) {
                closeSessions(ctx);
                projectPassiveLane(ctx, (Furnace) state);
            }
            saveToNbt(ctx);
        }
    }

    public static void shutdown() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            closeSessions(ctx);
        }
        flushAll();
        CONTEXTS.clear();
    }

    public static void closeSessions(@NotNull FurnaceContext ctx) {
        for (FureamScreenInventory session : ctx.sessions.values()) {
            for (org.bukkit.entity.HumanEntity viewer :
                new java.util.ArrayList<>(session.bukkitInventory.getViewers())) {
                viewer.closeInventory();
            }
        }
        ctx.sessions.clear();
    }

    private static void validateSessions(
        @NotNull FurnaceContext ctx, @NotNull Block block, boolean enabled
    ) {
        Iterator<Map.Entry<UUID, FureamScreenInventory>> iterator = ctx.sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FureamScreenInventory> entry = iterator.next();
            org.bukkit.entity.Player player = Bukkit.getPlayer(entry.getKey());
            boolean valid = enabled && player != null && player.isOnline() &&
                player.getWorld().equals(block.getWorld()) &&
                player.getLocation().distanceSquared(block.getLocation().add(0.5, 0.5, 0.5)) <= 64.0 &&
                FureamMain.getFurnaceType(block) == ctx.type &&
                player.getOpenInventory().getTopInventory().equals(entry.getValue().bukkitInventory);
            if (!valid) {
                if (player != null && player.isOnline()) player.closeInventory();
                ctx.sessions.remove(entry.getKey(), entry.getValue());
            }
        }
    }
}
