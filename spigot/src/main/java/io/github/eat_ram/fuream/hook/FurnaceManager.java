package io.github.eat_ram.fuream.hook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.data.FurnaceFureamDataRegistry;
import io.github.eat_ram.fuream.logic.FureamFurnaceEngine;
import io.github.eat_ram.fuream.nbt.FurnaceRootNbtBridge;
import io.github.eat_ram.fuream.screen.FureamScreenHandler;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.util.FurnacePos;
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
    private static final String NBT_API_MARKER_KEY = "__nbtapi";
    private static long tickCounter = 0;

    private static final class NbtLoadResult {
        private final boolean loaded;
        private final boolean hasLegacyData;

        private NbtLoadResult(boolean loaded, boolean hasLegacyData) {
            this.loaded = loaded;
            this.hasLegacyData = hasLegacyData;
        }
    }

    public static class FurnaceContext implements FureamDataHolder {
        public final @NotNull FurnacePos pos;
        public final @NotNull FurnaceType type;
        public final @NotNull FureamFurnaceData data;
        public final @NotNull Map<@NotNull String, @NotNull FureamData> extraData = new HashMap<>();

        public int burnTime;
        public int fuelTimeTotal;
        public int cookTime;
        public int cookTimeTotal;

        public @Nullable FureamScreenInventory activeGui;
        public @Nullable FureamScreenHandler handler;
        public boolean dirty;

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
                NbtLoadResult loadResult = NBT.get(state, (ReadableNBT rootNbt) -> {
                    ReadableNBT legacyNbt = getLegacyPdc(rootNbt);
                    boolean hasLegacyData = hasFureamStorage(legacyNbt);
                    ReadableNBT dataCompound = nativeLoaded ? null : getDataCompound(legacyNbt);

                    if (dataCompound != null) {
                        ctx.data.readNbt(ctx, dataCompound);
                        for (Map.Entry<String, FureamData> entry : ctx.extraData.entrySet()) {
                            entry.getValue().readNbt(ctx, dataCompound);
                        }
                        if (legacyNbt.hasTag("BurnTime")) {
                            ctx.burnTime = legacyNbt.getInteger("BurnTime");
                        }
                        if (legacyNbt.hasTag("CookTime")) {
                            ctx.cookTime = legacyNbt.getInteger("CookTime");
                        }
                        if (legacyNbt.hasTag("CookTimeTotal")) {
                            ctx.cookTimeTotal = legacyNbt.getInteger("CookTimeTotal");
                        }
                        return new NbtLoadResult(true, hasLegacyData);
                    }

                    return new NbtLoadResult(nativeLoaded, hasLegacyData);
                });

                if (!loadResult.loaded) {
                    migrateVanillaLane(ctx, (Furnace) state);
                }
                if (loadResult.hasLegacyData) {
                    ctx.dirty = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        CONTEXTS.put(pos, ctx);
        if (ctx.dirty) {
            saveToNbt(ctx);
        }
        return ctx;
    }

    public static void saveToNbt(@NotNull FurnaceContext ctx) {
        World world = ctx.getWorld();
        if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) return;

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        BlockState state = block.getState();
        if (state instanceof Furnace) {
            try {
                absorbVanillaLane(ctx, (Furnace) state);
                FurnaceRootNbtBridge.store(state, ctx);
                boolean hasLegacyData = NBT.get(
                    state,
                    (ReadableNBT rootNbt) -> hasFureamStorage(getLegacyPdc(rootNbt))
                );
                if (hasLegacyData) {
                    FurnaceRootNbtBridge.withoutInjection(
                        () -> NBT.modifyPersistentData(state, FurnaceManager::removeLegacyPdc)
                    );
                }
                ctx.dirty = false;
            } catch (Exception e) {
                e.printStackTrace();
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
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return null;
        }
        int maxSlots = Math.max(1, target.size());
        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(target, stack, maxSlots);
        return remainder.getType().isAir() ? null : remainder;
    }

    private static void migrateVanillaLane(
        @NotNull FurnaceContext ctx, @NotNull Furnace furnaceState
    ) {
        FurnaceInventory inv = furnaceState.getInventory();
        ItemStack smelting = inv.getSmelting();
        ItemStack fuel = inv.getFuel();
        ItemStack result = inv.getResult();

        if (smelting != null && !smelting.getType().isAir()) {
            ctx.data.inputs.set(0, smelting.clone());
        }
        if (fuel != null && !fuel.getType().isAir()) {
            ctx.data.fuels.set(0, fuel.clone());
        }
        if (result != null && !result.getType().isAir()) {
            ctx.data.outputs.set(0, result.clone());
        }

        inv.clear();
        ctx.burnTime = furnaceState.getBurnTime();
        ctx.cookTime = furnaceState.getCookTime();
        ctx.cookTimeTotal = furnaceState.getCookTimeTotal() > 0
            ? furnaceState.getCookTimeTotal()
            : (ctx.type == FurnaceType.FURNACE ? 200 : 100);
        ctx.dirty = true;
    }

    private static @Nullable ReadableNBT getDataCompound(@Nullable ReadableNBT nbt) {
        if (nbt == null || !nbt.hasTag(FureamMain.DATA_KEY)) {
            return null;
        }
        return nbt.getCompound(FureamMain.DATA_KEY);
    }

    private static @Nullable ReadableNBT getLegacyPdc(@NotNull ReadableNBT rootNbt) {
        if (!rootNbt.hasTag(PDC_ROOT_KEY)) {
            return null;
        }
        return rootNbt.getCompound(PDC_ROOT_KEY);
    }

    private static boolean hasFureamStorage(@Nullable ReadableNBT nbt) {
        return nbt != null && (
            nbt.hasTag(FureamMain.DATA_KEY) ||
            nbt.hasTag("BurnTime") ||
            nbt.hasTag("CookTime") ||
            nbt.hasTag("CookTimeTotal")
        );
    }

    private static void removeLegacyPdc(@NotNull ReadWriteNBT legacyNbt) {
        legacyNbt.removeKey(FureamMain.DATA_KEY);
        legacyNbt.removeKey("BurnTime");
        legacyNbt.removeKey("CookTime");
        legacyNbt.removeKey("CookTimeTotal");

        Set<String> remaining = legacyNbt.getKeys();
        if (remaining.size() == 1 && remaining.contains(NBT_API_MARKER_KEY)) {
            legacyNbt.removeKey(NBT_API_MARKER_KEY);
        }
    }

    public static void tickAll() {
        Iterator<Map.Entry<FurnacePos, FurnaceContext>> it = CONTEXTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FurnacePos, FurnaceContext> entry = it.next();
            FurnaceContext ctx = entry.getValue();
            World world = ctx.getWorld();

            if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) {
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
                if (ctx.dirty) {
                    saveToNbt(ctx);
                }
                it.remove();
                continue;
            }

            FureamWorldConfig config = FureamMain.getWorldConfig(world);
            if (config != null && config.getEnabledFurnaceTypes().contains(ctx.type)) {
                FureamFurnaceEngine.tick(ctx, config);

                // Handle hopper below extraction (every 8 ticks, standard hopper speed)
                if (tickCounter % 8 == 0) {
                    processHopperPull(block, ctx);
                }
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
            if (below.getBlockData() instanceof org.bukkit.block.data.type.Hopper) {
                org.bukkit.block.data.type.Hopper hopperData = (org.bukkit.block.data.type.Hopper) below.getBlockData();
                if (hopperData.isEnabled()) {
                    BlockState belowState = below.getState();
                    if (belowState instanceof Hopper) {
                        targetInv = ((Hopper) belowState).getInventory();
                    }
                }
            }
        }

        if (targetInv == null) {
            Location centerBelow = furnaceBlock.getLocation().add(0.5, -0.5, 0.5);
            for (org.bukkit.entity.Entity e : furnaceBlock.getWorld().getNearbyEntities(centerBelow, 0.6, 0.6, 0.6)) {
                if (e instanceof HopperMinecart) {
                    targetInv = ((HopperMinecart) e).getInventory();
                    break;
                }
            }
        }

        if (targetInv == null) {
            return;
        }

        // 1. Try pulling from outputs first
        for (int i = 0; i < ctx.data.outputs.size(); i++) {
            ItemStack outStack = ctx.data.outputs.get(i);
            if (!outStack.getType().isAir() && outStack.getAmount() > 0) {
                ItemStack single = outStack.clone();
                single.setAmount(1);

                Map<Integer, ItemStack> leftover = targetInv.addItem(single);
                if (leftover.isEmpty()) {
                    outStack.setAmount(outStack.getAmount() - 1);
                    if (outStack.getAmount() <= 0) {
                        ctx.data.outputs.set(i, new ItemStack(Material.AIR));
                    }
                    if (ctx.activeGui != null) {
                        ctx.activeGui.refreshVisuals();
                    }
                    ctx.dirty = true;
                    return;
                }
            }
        }

        // 2. Try pulling empty buckets or water buckets from fuels
        for (int i = 0; i < ctx.data.fuels.size(); i++) {
            ItemStack fuelStack = ctx.data.fuels.get(i);
            if (fuelStack.getType() == Material.BUCKET || fuelStack.getType() == Material.WATER_BUCKET) {
                ItemStack single = fuelStack.clone();
                single.setAmount(1);

                Map<Integer, ItemStack> leftover = targetInv.addItem(single);
                if (leftover.isEmpty()) {
                    fuelStack.setAmount(fuelStack.getAmount() - 1);
                    if (fuelStack.getAmount() <= 0) {
                        ctx.data.fuels.set(i, new ItemStack(Material.AIR));
                    }
                    if (ctx.activeGui != null) {
                        ctx.activeGui.refreshVisuals();
                    }
                    ctx.dirty = true;
                    return;
                }
            }
        }
    }

    public static void flushAll() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            if (ctx.dirty || ctx.data.hasAny()) {
                saveToNbt(ctx);
            }
        }
    }
}
