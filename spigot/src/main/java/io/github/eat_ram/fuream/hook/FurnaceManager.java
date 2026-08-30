package io.github.eat_ram.fuream.hook;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.ResolvedFurnaceConfig;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.FurnaceCompat;
import io.github.eat_ram.fuream.compat.FurnaceStateTransaction;
import io.github.eat_ram.fuream.compat.ComparatorCompat;
import io.github.eat_ram.fuream.compat.VersionAdapters;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.data.FurnaceFureamDataRegistry;
import io.github.eat_ram.fuream.logic.FureamFurnaceEngine;
import io.github.eat_ram.fuream.logic.NativeLaneProjection;
import io.github.eat_ram.fuream.logic.PassiveLaneProjection;
import io.github.eat_ram.fuream.logic.PassiveLaneProjection.SelectionMode;
import io.github.eat_ram.fuream.nbt.FurnaceRootNbtBridge;
import io.github.eat_ram.fuream.nbt.NativeNbtCompound;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.util.FurnacePos;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FurnaceManager {
    private static final int SAVE_INTERVAL_TICKS = 40;
    private static final Map<FurnacePos, FurnaceContext> CONTEXTS = new HashMap<>();
    private static final String PDC_ROOT_KEY = "PublicBukkitValues";
    private static long tickCounter = 0;

    public static final class FurnaceContext implements FureamDataHolder {
        public final @NotNull FurnacePos pos;
        public final @NotNull FurnaceType type;
        public final @NotNull FureamFurnaceData data;
        public final @NotNull Map<@NotNull String, @NotNull FureamData> extraData = new HashMap<>();

        public int burnTime;
        public int fuelTimeTotal;
        public int cookTime;
        public int cookTimeTotal;

        public final @NotNull Map<UUID, FureamScreenInventory> sessions = new HashMap<>();
        public boolean dirty;
        public boolean passiveProjected;
        public final @NotNull PassiveLaneProjection.State passiveInput =
            new PassiveLaneProjection.State();
        public final @NotNull PassiveLaneProjection.State passiveFuel =
            new PassiveLaneProjection.State();
        public final @NotNull PassiveLaneProjection.State passiveOutput =
            new PassiveLaneProjection.State();
        public boolean activeProjected;
        public final @NotNull NativeLaneProjection.State activeFuel =
            new NativeLaneProjection.State();
        public final @NotNull NativeLaneProjection.State activeOutput =
            new NativeLaneProjection.State();
        public int lastInputSlot = -1;
        public @Nullable KeyableItemStack lastInputKey;
        public boolean startSmeltPending = true;
        public int lastComparatorSignal = -1;
        public @Nullable Boolean lastLit;
        private @Nullable FureamWorldConfig resolvedSource;
        private @Nullable ResolvedFurnaceConfig resolvedConfig;

        public FurnaceContext(@NotNull FurnacePos pos, @NotNull FurnaceType type) {
            this.pos = pos;
            this.type = type;
            this.data = new FureamFurnaceData();
            this.cookTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
            this.fuelTimeTotal = 200;
        }

        public @Nullable World getWorld() {
            World byId = this.pos.worldId == null ? null : Bukkit.getWorld(this.pos.worldId);
            return byId == null ? Bukkit.getWorld(this.pos.worldName) : byId;
        }

        public @NotNull ResolvedFurnaceConfig resolve(@NotNull FureamWorldConfig config) {
            if (this.resolvedConfig == null || this.resolvedSource != config) {
                this.resolvedSource = config;
                this.resolvedConfig = ResolvedFurnaceConfig.resolve(config, this.type);
            }
            return this.resolvedConfig;
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

    public static @Nullable FurnaceContext getContext(@NotNull FurnacePos pos) {
        return CONTEXTS.get(pos);
    }

    public static @Nullable FurnaceContext getContext(@NotNull Block block) {
        return CONTEXTS.get(new FurnacePos(block));
    }

    public static int contextCount() {
        return CONTEXTS.size();
    }

    public static boolean isManagedActive(@NotNull Block block) {
        FurnaceContext context = getContext(block);
        return context != null && FureamMain.getFurnaceType(block) == context.type &&
            isEnabled(block.getWorld(), context.type);
    }

    public static @Nullable FurnaceContext prepareForRemoval(@NotNull Block block) {
        FurnaceContext context = getContext(block);
        if (context == null || FureamMain.getFurnaceType(block) != context.type) return context;
        FureamWorldConfig config = FureamMain.getWorldConfig(block.getWorld());
        if (config != null && !context.passiveProjected) {
            prepareActiveTransaction(context, block, config);
        }
        closeSessions(context);
        return context;
    }

    public static @Nullable FurnaceContext prepareForInteraction(@NotNull FurnacePos pos) {
        FurnaceContext context = getContext(pos);
        if (context == null) return null;
        World world = context.getWorld();
        if (world == null || !world.isChunkLoaded(pos.x >> 4, pos.z >> 4)) return null;
        Block block = world.getBlockAt(pos.x, pos.y, pos.z);
        FureamWorldConfig config = FureamMain.getWorldConfig(world);
        if (config == null || FureamMain.getFurnaceType(block) != context.type) return null;
        if (!context.passiveProjected) prepareActiveTransaction(context, block, config);
        return context;
    }

    public static void finishInteraction(@NotNull FurnaceContext context) {
        World world = context.getWorld();
        if (world == null || !isEnabled(world, context.type) ||
            !world.isChunkLoaded(context.pos.x >> 4, context.pos.z >> 4)) return;
        Block block = world.getBlockAt(context.pos.x, context.pos.y, context.pos.z);
        FureamWorldConfig config = FureamMain.getWorldConfig(world);
        if (config != null && FureamMain.getFurnaceType(block) == context.type) {
            publishActiveTransaction(context, block, config);
        }
    }

    public static @Nullable FurnaceContext getOrCreateContext(@NotNull Block block) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return null;

        FurnacePos pos = new FurnacePos(block);
        FurnaceContext existing = CONTEXTS.get(pos);
        if (existing != null) {
            if (existing.type == type) return existing;
            closeSessions(existing);
            CONTEXTS.remove(pos);
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
                    ctx.passiveProjected = FurnaceRootNbtBridge.hasPassiveProjectionMarker(
                        dataCompound
                    );
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
                    if (ctx.passiveProjected) {
                        resumeActive(ctx, block);
                    } else {
                        FureamWorldConfig config = FureamMain.getWorldConfig(block.getWorld());
                        if (config != null) prepareActiveTransaction(ctx, block, config);
                    }
                } else if (!ctx.passiveProjected) {
                    projectPassiveLane(ctx, block);
                } else {
                    syncPassiveTimes(ctx, (Furnace) state);
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
        saveToNbt(ctx, true);
    }

    private static void saveToNbt(@NotNull FurnaceContext ctx, boolean republishAfter) {
        World world = ctx.getWorld();
        if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) return;

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        if (FureamMain.getFurnaceType(block) != ctx.type) return;
        boolean republish = false;
        try {
            FureamWorldConfig config = FureamMain.getWorldConfig(world);
            if (ctx.passiveProjected) {
                BlockState state = block.getState();
                if (state instanceof Furnace) syncPassiveTimes(ctx, (Furnace) state);
            } else if (config != null) {
                prepareActiveTransaction(ctx, block, config);
                republish = republishAfter && isEnabled(world, ctx.type);
            }
            FurnaceRootNbtBridge.store(block.getState(), ctx);
            ctx.dirty = false;
        } catch (Exception e) {
            FureamMain.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                "Unable to save furnace data at " + ctx.pos, e);
        } finally {
            if (republish) {
                FureamWorldConfig config = FureamMain.getWorldConfig(world);
                if (config != null && FureamMain.getFurnaceType(block) == ctx.type) {
                    publishActiveTransaction(ctx, block, config);
                }
            }
        }
    }

    private static @Nullable ItemStack absorbLaneStack(
        @NotNull List<ItemStack> target, @Nullable ItemStack stack, int maxSlots
    ) {
        if (ItemCompat.isEmpty(stack)) {
            return null;
        }
        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(
            target, stack.clone(), Math.max(1, maxSlots)
        );
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static void migrateVanillaLane(
        @NotNull FurnaceContext ctx, @NotNull Furnace furnaceState
    ) {
        FurnaceStateTransaction transaction = VersionAdapters.current().beginFurnaceTransaction(furnaceState.getBlock());
        if (transaction == null) return;
        FurnaceInventory inv = transaction.inventory();
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

        inv.setSmelting(null);
        inv.setFuel(null);
        inv.setResult(null);
        transaction.markChanged();
        ctx.burnTime = FurnaceCompat.getBurnTime(furnaceState);
        ctx.cookTime = FurnaceCompat.getCookTime(furnaceState);
        int nativeTotal = FurnaceCompat.getCookTimeTotal(
            furnaceState, ctx.type == FurnaceType.FURNACE ? 200 : 100
        );
        ctx.cookTimeTotal = nativeTotal > 0
            ? nativeTotal
            : (ctx.type == FurnaceType.FURNACE ? 200 : 100);
        transaction.setTimes(0, 0, ctx.cookTimeTotal);
        requireCommit(transaction, ctx);
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

    private static void projectPassiveLane(@NotNull FurnaceContext ctx, @NotNull Block block) {
        closeSessions(ctx);
        FurnaceStateTransaction transaction = VersionAdapters.current().beginFurnaceTransaction(block);
        if (transaction == null) return;
        FurnaceInventory inventory = transaction.inventory();
        inventory.setSmelting(PassiveLaneProjection.begin(
            ctx.data.inputs, inventory.getSmelting(), SelectionMode.FIRST_PRESENT, ctx.passiveInput
        ));
        inventory.setFuel(PassiveLaneProjection.begin(
            ctx.data.fuels, inventory.getFuel(), SelectionMode.FUEL_THEN_PRESENT, ctx.passiveFuel
        ));
        inventory.setResult(PassiveLaneProjection.begin(
            ctx.data.outputs, inventory.getResult(), SelectionMode.FIRST_PRESENT, ctx.passiveOutput
        ));
        transaction.markChanged();
        transaction.setTimes(
            Math.max(0, ctx.burnTime), Math.max(0, ctx.cookTime), Math.max(1, ctx.cookTimeTotal)
        );
        requireCommit(transaction, ctx);
        ctx.passiveProjected = true;
        ctx.dirty = true;
    }

    private static int firstPresentSlot(@NotNull java.util.List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemCompat.isEmpty(stacks.get(i))) return i;
        }
        return -1;
    }

    private static void resumeActive(@NotNull FurnaceContext ctx, @NotNull Block block) {
        if (!ctx.passiveProjected) return;
        FurnaceStateTransaction transaction = VersionAdapters.current().beginFurnaceTransaction(block);
        if (transaction == null) return;
        Furnace furnace = transaction.state();
        FurnaceInventory inventory = transaction.inventory();
        ctx.burnTime = FurnaceCompat.getBurnTime(furnace);
        ctx.cookTime = FurnaceCompat.getCookTime(furnace);
        ctx.cookTimeTotal = FurnaceCompat.getCookTimeTotal(furnace, ctx.cookTimeTotal);
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
        transaction.markChanged();
        transaction.setTimes(0, 0, Math.max(1, ctx.cookTimeTotal));
        requireCommit(transaction, ctx);
        ctx.passiveProjected = false;
        ctx.dirty = true;
    }

    private static void syncPassiveTimes(@NotNull FurnaceContext ctx, @NotNull Furnace furnace) {
        ctx.burnTime = FurnaceCompat.getBurnTime(furnace);
        ctx.cookTime = FurnaceCompat.getCookTime(furnace);
        ctx.cookTimeTotal = FurnaceCompat.getCookTimeTotal(furnace, ctx.cookTimeTotal);
    }

    /** Reconciles hopper/player changes and absorbs native input buffers before custom logic. */
    public static void prepareActiveTransaction(
        @NotNull FurnaceContext ctx, @NotNull Block block, @NotNull FureamWorldConfig config
    ) {
        FurnaceStateTransaction transaction = VersionAdapters.current().beginFurnaceTransaction(block);
        if (transaction == null) return;
        FurnaceInventory inventory = transaction.inventory();
        ResolvedFurnaceConfig resolved = ctx.resolve(config);
        int inputCount = resolved.inputSlots;
        int fuelCount = resolved.fuelSlots;
        int outputCount = resolved.outputSlots;
        ensureSize(ctx.data.inputs, inputCount);
        ensureSize(ctx.data.fuels, fuelCount);
        ensureSize(ctx.data.outputs, outputCount);

        boolean changed = false;
        boolean logicalChange = false;
        if (ctx.activeProjected) {
            ItemStack observedFuel = inventory.getFuel();
            ItemStack observedOutput = inventory.getResult();
            logicalChange = !sameStack(observedFuel, ctx.activeFuel.getPublished()) ||
                !sameStack(observedOutput, ctx.activeOutput.getPublished());
            ItemStack fuelRemainder = NativeLaneProjection.reconcile(
                ctx.data.fuels, observedFuel, fuelCount, ctx.activeFuel
            );
            ItemStack outputRemainder = NativeLaneProjection.reconcile(
                ctx.data.outputs, observedOutput, outputCount, ctx.activeOutput
            );
            inventory.setFuel(ItemCompat.isEmpty(fuelRemainder) ? null : fuelRemainder);
            inventory.setResult(ItemCompat.isEmpty(outputRemainder) ? null : outputRemainder);
            ctx.activeProjected = false;
            changed = true;
        }

        ItemStack nativeInput = inventory.getSmelting();
        if (!ItemCompat.isEmpty(nativeInput)) {
            inventory.setSmelting(absorbLaneStack(ctx.data.inputs, nativeInput, inputCount));
            changed = true;
            logicalChange = true;
        }
        ItemStack nativeFuel = inventory.getFuel();
        if (!ItemCompat.isEmpty(nativeFuel)) {
            inventory.setFuel(absorbLaneStack(ctx.data.fuels, nativeFuel, fuelCount));
            changed = true;
            logicalChange = true;
        }
        ItemStack nativeResult = inventory.getResult();
        if (!ItemCompat.isEmpty(nativeResult)) {
            inventory.setResult(absorbLaneStack(ctx.data.outputs, nativeResult, outputCount));
            changed = true;
            logicalChange = true;
        }

        if (changed) {
            transaction.markChanged();
        }
        if (logicalChange) ctx.dirty = true;
        transaction.setTimes(0, 0, Math.max(1, ctx.cookTimeTotal));
        requireCommit(transaction, ctx);
    }

    /** Publishes extractable stacks to native slots so Bukkit handles hoppers and minecarts. */
    public static void publishActiveTransaction(
        @NotNull FurnaceContext ctx, @NotNull Block block, @NotNull FureamWorldConfig config
    ) {
        FurnaceStateTransaction transaction = VersionAdapters.current().beginFurnaceTransaction(block);
        if (transaction == null) return;
        FurnaceInventory inventory = transaction.inventory();
        ResolvedFurnaceConfig resolved = ctx.resolve(config);
        int fuelCount = resolved.fuelSlots;
        int outputCount = resolved.outputSlots;
        ensureSize(ctx.data.fuels, fuelCount);
        ensureSize(ctx.data.outputs, outputCount);

        ItemStack fuel = NativeLaneProjection.publish(
            ctx.data.fuels, inventory.getFuel(),
            NativeLaneProjection.SelectionMode.BUCKET_REMAINDER, ctx.activeFuel
        );
        ItemStack output = NativeLaneProjection.publish(
            ctx.data.outputs, inventory.getResult(),
            NativeLaneProjection.SelectionMode.FIRST_PRESENT, ctx.activeOutput
        );
        inventory.setFuel(ItemCompat.isEmpty(fuel) ? null : fuel);
        inventory.setResult(ItemCompat.isEmpty(output) ? null : output);
        ctx.activeProjected = ctx.activeFuel.isActive() || ctx.activeOutput.isActive();
        if (ctx.activeProjected) {
            transaction.markChanged();
        }
        transaction.setTimes(0, 0, Math.max(1, ctx.cookTimeTotal));
        requireCommit(transaction, ctx);
        FurnaceRootNbtBridge.updateProjectionMarker(block.getState(), ctx);
    }

    private static void requireCommit(
        @NotNull FurnaceStateTransaction transaction, @NotNull FurnaceContext ctx
    ) {
        if (!transaction.commit()) {
            throw new IllegalStateException("Furnace state changed before commit at " + ctx.pos);
        }
    }

    private static void ensureSize(@NotNull List<ItemStack> slots, int size) {
        while (slots.size() < size) slots.add(ItemCompat.empty());
    }

    private static boolean sameStack(@Nullable ItemStack left, @Nullable ItemStack right) {
        return ItemCompat.isEmpty(left) && ItemCompat.isEmpty(right) ||
            !ItemCompat.isEmpty(left) && !ItemCompat.isEmpty(right) &&
                left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    public static void tickAll() {
        List<Block> replacementFurnaces = new ArrayList<>();
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
                closeSessions(ctx);
                it.remove();
                continue;
            }
            if (currentType != ctx.type) {
                closeSessions(ctx);
                it.remove();
                replacementFurnaces.add(block);
                continue;
            }

            FureamWorldConfig config = FureamMain.getWorldConfig(world);
            boolean enabled = config != null && config.getEnabledFurnaceTypes().contains(ctx.type);
            validateSessions(ctx, block, enabled);
            BlockState state = block.getState();
            if (enabled) {
                if (ctx.passiveProjected && state instanceof Furnace) {
                    resumeActive(ctx, block);
                }
                prepareActiveTransaction(ctx, block, config);
                FureamFurnaceEngine.tick(ctx, ctx.resolve(config));
                int comparatorSignal = ComparatorCompat.calculate(ctx.data);
                if (comparatorSignal != ctx.lastComparatorSignal) {
                    VersionAdapters.current().requestComparatorRecalculation(block);
                    ctx.lastComparatorSignal = comparatorSignal;
                }
                boolean lit = ctx.burnTime > 0;
                if (ctx.lastLit == null || ctx.lastLit != lit) {
                    VersionAdapters.current().setFurnaceLit(block, lit);
                    ctx.lastLit = lit;
                }
                publishActiveTransaction(ctx, block, config);
                refreshSessions(ctx);
            } else if (!ctx.passiveProjected && state instanceof Furnace) {
                if (ctx.activeProjected && config != null) {
                    prepareActiveTransaction(ctx, block, config);
                }
                projectPassiveLane(ctx, block);
            } else if (ctx.passiveProjected && state instanceof Furnace) {
                syncPassiveTimes(ctx, (Furnace) state);
            }

            if (ctx.dirty && tickCounter % SAVE_INTERVAL_TICKS == 0) {
                saveToNbt(ctx);
            }
        }
        for (Block block : replacementFurnaces) getOrCreateContext(block);
        tickCounter++;
    }

    public static void flushAll() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            if (ctx.dirty || ctx.data.hasAny()) {
                saveToNbt(ctx);
            }
        }
    }

    public static void flushWorld(@NotNull World world) {
        for (FurnaceContext ctx : new ArrayList<>(CONTEXTS.values())) {
            if (sameWorld(ctx.pos, world) && (ctx.dirty || ctx.data.hasAny())) saveToNbt(ctx);
        }
    }

    public static void unloadWorld(@NotNull World world) {
        Iterator<Map.Entry<FurnacePos, FurnaceContext>> iterator = CONTEXTS.entrySet().iterator();
        while (iterator.hasNext()) {
            FurnaceContext ctx = iterator.next().getValue();
            if (!sameWorld(ctx.pos, world)) continue;
            closeSessions(ctx);
            if (ctx.dirty || ctx.data.hasAny()) saveToNbt(ctx, false);
            iterator.remove();
        }
    }

    public static void unloadChunk(@NotNull org.bukkit.Chunk chunk) {
        Iterator<Map.Entry<FurnacePos, FurnaceContext>> iterator = CONTEXTS.entrySet().iterator();
        while (iterator.hasNext()) {
            FurnaceContext ctx = iterator.next().getValue();
            if (!sameWorld(ctx.pos, chunk.getWorld()) ||
                (ctx.pos.x >> 4) != chunk.getX() || (ctx.pos.z >> 4) != chunk.getZ()) continue;
            closeSessions(ctx);
            if (ctx.dirty || ctx.data.hasAny()) saveToNbt(ctx, false);
            iterator.remove();
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
                resumeActive(ctx, block);
                initializeInputIdentity(ctx);
            } else if (!enabled && !ctx.passiveProjected) {
                closeSessions(ctx);
                FureamWorldConfig config = FureamMain.getWorldConfig(world);
                if (config != null && ctx.activeProjected) prepareActiveTransaction(ctx, block, config);
                projectPassiveLane(ctx, block);
            }
            saveToNbt(ctx);
        }
    }

    public static void shutdown() {
        for (FurnaceContext ctx : new ArrayList<>(CONTEXTS.values())) {
            closeSessions(ctx);
            World world = ctx.getWorld();
            if (world != null && world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) {
                Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
                FureamWorldConfig config = FureamMain.getWorldConfig(world);
                if (config != null && ctx.activeProjected && FureamMain.getFurnaceType(block) == ctx.type) {
                    prepareActiveTransaction(ctx, block, config);
                }
            }
        }
        for (FurnaceContext ctx : CONTEXTS.values()) {
            if (ctx.dirty || ctx.data.hasAny()) saveToNbt(ctx, false);
        }
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

    private static boolean sameWorld(@NotNull FurnacePos pos, @NotNull World world) {
        return pos.worldId != null ? pos.worldId.equals(world.getUID()) : pos.worldName.equals(world.getName());
    }

    private FurnaceManager() {
    }
}
