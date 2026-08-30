package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.NativeLaneProjection;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Bridges instrumented NMS furnace serialization to the Bukkit-side context. */
public final class FurnaceRootNbtBridge {
    private static final String PASSIVE_PROJECTION_KEY = "PassiveProjected";
    private static final String ACTIVE_PROJECTION_KEY = "SpigotProjection";
    private static final String FUEL_SLOT_KEY = "FuelSlot";
    private static final String FUEL_AMOUNT_KEY = "FuelAmount";
    private static final String OUTPUT_SLOT_KEY = "OutputSlot";
    private static final String OUTPUT_AMOUNT_KEY = "OutputAmount";

    private static final ConcurrentMap<Class<?>, Method> TILE_ENTITY_METHODS = new ConcurrentHashMap<>();
    private static volatile Method runtimeGetState;
    private static volatile Method runtimePutState;
    private static volatile Method runtimeSetSuppressed;
    private static volatile Method runtimeFailureCount;
    private static volatile Method runtimeLastFailure;

    public static void bindRuntime(@NotNull Class<?> runtimeClass) {
        try {
            runtimeGetState = runtimeClass.getMethod("getState", Object.class);
            runtimePutState = runtimeClass.getMethod("putState", Object.class, Object[].class);
            runtimeSetSuppressed = runtimeClass.getMethod("setSuppressed", boolean.class);
            runtimeFailureCount = runtimeClass.getMethod("getFailureCount");
            runtimeLastFailure = runtimeClass.getMethod("getLastFailure");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to bind the root-NBT runtime", e);
        }
    }

    public static boolean loadNativeData(@NotNull BlockState state, @NotNull FurnaceContext ctx) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) return false;
        Object[] stored = runtimeState(tileEntity);
        if (stored == null || stored.length < 2 || stored[0] == null) return false;

        NativeNbtCompound dataNbt = new NativeNbtCompound(stored[0]);
        ctx.data.readNbt(ctx, dataNbt);
        for (Map.Entry<String, FureamData> entry : ctx.extraData.entrySet()) {
            entry.getValue().readNbt(ctx, dataNbt);
        }
        ctx.passiveProjected = hasPassiveProjectionMarker(dataNbt);
        readActiveProjectionMarker(dataNbt, ctx);

        if (stored[1] != null && !(stored[1] instanceof Integer)) {
            NativeNbtCompound rootNbt = new NativeNbtCompound(stored[1]);
            ctx.burnTime = readNumericTag(rootNbt, "BurnTime", ctx.burnTime);
            ctx.cookTime = readNumericTag(rootNbt, "CookTime", ctx.cookTime);
            ctx.cookTimeTotal = readNumericTag(rootNbt, "CookTimeTotal", ctx.cookTimeTotal);
        } else if (stored.length >= 4) {
            ctx.burnTime = (Integer) stored[1];
            ctx.cookTime = (Integer) stored[2];
            ctx.cookTimeTotal = (Integer) stored[3];
        }

        putRuntimeState(tileEntity, new Object[] {
            stored[0], ctx.burnTime, ctx.cookTime, ctx.cookTimeTotal
        });
        return true;
    }

    public static void store(@NotNull BlockState state, @NotNull FurnaceContext ctx) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) {
            throw new IllegalStateException("Unable to access the live furnace block entity");
        }

        NativeNbtCompound dataNbt = NativeNbtCompound.createForServer();
        ctx.data.writeNbt(ctx, dataNbt);
        for (FureamData extra : ctx.extraData.values()) extra.writeNbt(ctx, dataNbt);
        if (ctx.passiveProjected) dataNbt.setInt(PASSIVE_PROJECTION_KEY, 1);
        writeActiveProjectionMarker(dataNbt, ctx);
        boolean dataEmpty = dataNbt.keys().isEmpty();
        putRuntimeState(tileEntity, new Object[] {
            shouldKeepDataTag(dataEmpty, ctx.passiveProjected || ctx.activeProjected)
                ? dataNbt.raw() : null,
            ctx.burnTime, ctx.cookTime, ctx.cookTimeTotal
        });
        markTileDirty(tileEntity);
    }

    public static void updateProjectionMarker(@NotNull BlockState state, @NotNull FurnaceContext ctx) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) return;
        Object[] stored = runtimeState(tileEntity);
        if (stored == null || stored.length == 0 || stored[0] == null) {
            if (ctx.activeProjected) store(state, ctx);
            return;
        }
        writeActiveProjectionMarker(new NativeNbtCompound(stored[0]), ctx);
        markTileDirty(tileEntity);
    }

    public static boolean hasPassiveProjectionMarker(@Nullable NativeNbtCompound dataNbt) {
        return dataNbt != null && dataNbt.getInt(PASSIVE_PROJECTION_KEY, 0) != 0;
    }

    static boolean shouldKeepDataTag(boolean dataEmpty, boolean projected) {
        return !dataEmpty || projected;
    }

    public static @Nullable NativeNbtCompound capturedRoot(@NotNull BlockState state) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) return null;
        Object[] stored = runtimeState(tileEntity);
        if (stored == null || stored.length < 2 || stored[1] == null || stored[1] instanceof Integer) {
            return null;
        }
        return new NativeNbtCompound(stored[1]);
    }

    public static void withoutInjection(@NotNull Runnable action) {
        setRuntimeSuppressed(true);
        try {
            action.run();
        } finally {
            setRuntimeSuppressed(false);
        }
    }

    public static long failureCount() {
        try {
            Method method = runtimeFailureCount;
            return method == null ? 0L : ((Number) method.invoke(null)).longValue();
        } catch (ReflectiveOperationException e) {
            return -1L;
        }
    }

    public static @NotNull String lastFailure() {
        try {
            Method method = runtimeLastFailure;
            Object value = method == null ? "runtime unavailable" : method.invoke(null);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException e) {
            return e.toString();
        }
    }

    private static @Nullable Object[] runtimeState(Object tileEntity) {
        Method method = runtimeGetState;
        if (method == null) return null;
        try {
            return (Object[]) method.invoke(null, tileEntity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read root-NBT runtime state", e);
        }
    }

    private static void putRuntimeState(Object tileEntity, Object[] state) {
        Method method = runtimePutState;
        if (method == null) throw new IllegalStateException("Root-NBT runtime is unavailable");
        try {
            method.invoke(null, tileEntity, (Object) state);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to update root-NBT runtime state", e);
        }
    }

    private static void setRuntimeSuppressed(boolean suppressed) {
        Method method = runtimeSetSuppressed;
        if (method == null) return;
        try {
            method.invoke(null, suppressed);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to change root-NBT runtime scope", e);
        }
    }

    private static @Nullable Object getTileEntity(@NotNull BlockState state) {
        try {
            Method method = TILE_ENTITY_METHODS.get(state.getClass());
            if (method == null) {
                method = findTileEntityMethod(state.getClass());
                TILE_ENTITY_METHODS.put(state.getClass(), method);
            }
            return method.invoke(state);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static @NotNull Method findTileEntityMethod(@NotNull Class<?> stateClass)
        throws NoSuchMethodException {
        for (String preferred : new String[] {"getTileEntityFromWorld", "getTileEntity"}) {
            for (Class<?> current = stateClass; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (preferred.equals(method.getName()) && method.getParameterTypes().length == 0) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        throw new NoSuchMethodException("No live tile entity accessor on " + stateClass.getName());
    }

    private static void markTileDirty(Object tileEntity) {
        for (String name : new String[] {"setChanged", "markDirty"}) {
            try {
                Method method = tileEntity.getClass().getMethod(name);
                method.invoke(tileEntity);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static int readNumericTag(@Nullable NativeNbtCompound nbt, @NotNull String key, int fallback) {
        return nbt == null ? fallback : nbt.getInt(key, fallback);
    }

    private static void writeActiveProjectionMarker(
        @NotNull NativeNbtCompound dataNbt, @NotNull FurnaceContext ctx
    ) {
        if (!ctx.activeProjected) {
            dataNbt.remove(ACTIVE_PROJECTION_KEY);
            return;
        }
        NativeNbtCompound marker = dataNbt.getOrCreateCompound(ACTIVE_PROJECTION_KEY);
        writeLaneMarker(marker, FUEL_SLOT_KEY, FUEL_AMOUNT_KEY, ctx.activeFuel);
        writeLaneMarker(marker, OUTPUT_SLOT_KEY, OUTPUT_AMOUNT_KEY, ctx.activeOutput);
    }

    private static void writeLaneMarker(
        NativeNbtCompound marker, String slotKey, String amountKey, NativeLaneProjection.State state
    ) {
        ItemStack published = state.getPublished();
        marker.setInt(slotKey, state.getSourceSlot());
        marker.setInt(amountKey, published == null ? 0 : published.getAmount());
    }

    private static void readActiveProjectionMarker(
        @NotNull NativeNbtCompound dataNbt, @NotNull FurnaceContext ctx
    ) {
        NativeNbtCompound marker = dataNbt.getCompound(ACTIVE_PROJECTION_KEY);
        if (marker == null) return;
        boolean fuel = restoreLaneMarker(
            marker, FUEL_SLOT_KEY, FUEL_AMOUNT_KEY, ctx.data.fuels, ctx.activeFuel
        );
        boolean output = restoreLaneMarker(
            marker, OUTPUT_SLOT_KEY, OUTPUT_AMOUNT_KEY, ctx.data.outputs, ctx.activeOutput
        );
        ctx.activeProjected = fuel || output;
    }

    private static boolean restoreLaneMarker(
        NativeNbtCompound marker, String slotKey, String amountKey,
        java.util.List<ItemStack> lane, NativeLaneProjection.State state
    ) {
        int slot = marker.getInt(slotKey, -1);
        int amount = marker.getInt(amountKey, 0);
        if (slot < 0 || slot >= lane.size() || amount <= 0) return false;
        ItemStack stack = lane.get(slot);
        if (io.github.eat_ram.fuream.compat.ItemCompat.isEmpty(stack) || stack.getAmount() != amount) {
            return false;
        }
        state.restore(slot, stack);
        return true;
    }

    private FurnaceRootNbtBridge() {
    }
}
