package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import org.bukkit.block.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Bridges instrumented NMS furnace serialization to the Bukkit-side context. */
public final class FurnaceRootNbtBridge {
    private static final String LOAD_HOOK_KEY = "io.github.eat_ram.fuream.root_nbt_load_hook";
    private static final String SAVE_HOOK_KEY = "io.github.eat_ram.fuream.root_nbt_save_hook";
    private static final String STATE_MAP_KEY = "io.github.eat_ram.fuream.root_nbt_state";

    private static final ConcurrentMap<Class<?>, Field> TAG_MAP_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> PUT_SHORT_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> TILE_ENTITY_METHODS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> INJECTION_SUPPRESSED = new ThreadLocal<>();

    public static void installCallbacks() {
        Properties properties = System.getProperties();
        properties.put(LOAD_HOOK_KEY, (BiConsumer<Object, Object>) FurnaceRootNbtBridge::captureLoadedNbt);
        properties.put(SAVE_HOOK_KEY, (BiConsumer<Object, Object>) FurnaceRootNbtBridge::injectSavedNbt);
        stateMap();
    }

    public static boolean loadNativeData(
        @NotNull BlockState state, @NotNull FurnaceContext ctx
    ) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) {
            return false;
        }

        Object[] stored = stateMap().get(tileEntity);
        if (stored == null || stored.length < 2 || stored[0] == null) {
            return false;
        }

        ReadableNBT dataNbt = NBT.wrapNMSTag(stored[0]);
        ctx.data.readNbt(ctx, dataNbt);
        for (Map.Entry<String, FureamData> entry : ctx.extraData.entrySet()) {
            entry.getValue().readNbt(ctx, dataNbt);
        }

        if (stored[1] != null && !(stored[1] instanceof Integer)) {
            ReadableNBT rootNbt = NBT.wrapNMSTag(stored[1]);
            ctx.burnTime = readNumericTag(rootNbt, "BurnTime", ctx.burnTime);
            ctx.cookTime = readNumericTag(rootNbt, "CookTime", ctx.cookTime);
            ctx.cookTimeTotal = readNumericTag(rootNbt, "CookTimeTotal", ctx.cookTimeTotal);
        } else if (stored.length >= 4) {
            ctx.burnTime = (Integer) stored[1];
            ctx.cookTime = (Integer) stored[2];
            ctx.cookTimeTotal = (Integer) stored[3];
        }

        stateMap().put(tileEntity, new Object[] {
            stored[0], ctx.burnTime, ctx.cookTime, ctx.cookTimeTotal
        });
        return true;
    }

    public static void store(
        @NotNull BlockState state, @NotNull FurnaceContext ctx
    ) {
        Object tileEntity = getTileEntity(state);
        if (tileEntity == null) {
            throw new IllegalStateException("Unable to access the live furnace block entity");
        }

        Object[] previous = stateMap().get(tileEntity);
        Object previousData = previous != null && previous.length > 0 ? previous[0] : null;
        ReadWriteNBT dataNbt = NBT.createNBTObject();
        if (previousData != null) {
            dataNbt.mergeCompound(NBT.wrapNMSTag(previousData));
        }

        ctx.data.writeNbt(ctx, dataNbt);
        for (FureamData extra : ctx.extraData.values()) {
            extra.writeNbt(ctx, dataNbt);
        }

        Object rawData = rawCompound(dataNbt);
        stateMap().put(tileEntity, new Object[] {
            rawData, ctx.burnTime, ctx.cookTime, ctx.cookTimeTotal
        });
    }

    public static void withoutInjection(@NotNull Runnable action) {
        INJECTION_SUPPRESSED.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            INJECTION_SUPPRESSED.remove();
        }
    }

    private static void captureLoadedNbt(Object tileEntity, Object rootNbt) {
        try {
            Object fureamData = tagMap(rootNbt).get(FureamMain.DATA_KEY);
            if (fureamData != null) {
                stateMap().put(tileEntity, new Object[] {fureamData, rootNbt});
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to capture furnace root NBT", e);
        }
    }

    private static void injectSavedNbt(Object tileEntity, Object rootNbt) {
        if (Boolean.TRUE.equals(INJECTION_SUPPRESSED.get())) {
            return;
        }
        try {
            Object[] stored = stateMap().get(tileEntity);
            if (stored == null || stored.length == 0) {
                return;
            }

            if (stored[0] != null) {
                tagMap(rootNbt).put(FureamMain.DATA_KEY, stored[0]);
            }
            if (stored.length < 4 || !(stored[1] instanceof Integer)) {
                return;
            }
            putShort(rootNbt, "BurnTime", (short) (int) (Integer) stored[1]);
            putShort(rootNbt, "CookTime", (short) (int) (Integer) stored[2]);
            putShort(rootNbt, "CookTimeTotal", (short) (int) (Integer) stored[3]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to inject furnace root NBT", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static @NotNull Map<Object, Object[]> stateMap() {
        Properties properties = System.getProperties();
        synchronized (properties) {
            Object existing = properties.get(STATE_MAP_KEY);
            if (existing instanceof Map) {
                return (Map<Object, Object[]>) existing;
            }
            Map<Object, Object[]> created = Collections.synchronizedMap(new WeakHashMap<>());
            properties.put(STATE_MAP_KEY, created);
            return created;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tagMap(Object compound) throws ReflectiveOperationException {
        Field field = TAG_MAP_FIELDS.get(compound.getClass());
        if (field == null) {
            field = findTagMapField(compound.getClass());
            TAG_MAP_FIELDS.put(compound.getClass(), field);
        }
        return (Map<String, Object>) field.get(compound);
    }

    private static Field findTagMapField(Class<?> compoundClass) {
        for (Field field : compoundClass.getFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        for (Field field : compoundClass.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Unable to locate NBT compound tag map on " + compoundClass.getName());
    }

    private static void putShort(Object compound, String key, short value)
        throws ReflectiveOperationException {
        Method method = PUT_SHORT_METHODS.get(compound.getClass());
        if (method == null) {
            method = findPutShortMethod(compound.getClass());
            PUT_SHORT_METHODS.put(compound.getClass(), method);
        }
        method.invoke(compound, key, value);
    }

    private static Method findPutShortMethod(Class<?> compoundClass) {
        for (Method method : compoundClass.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == String.class &&
                parameters[1] == short.class && method.getReturnType() == void.class) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IllegalStateException("Unable to locate NBT putShort on " + compoundClass.getName());
    }

    private static @Nullable Object getTileEntity(@NotNull BlockState state) {
        try {
            Method method = TILE_ENTITY_METHODS.get(state.getClass());
            if (method == null) {
                method = state.getClass().getMethod("getTileEntity");
                method.setAccessible(true);
                TILE_ENTITY_METHODS.put(state.getClass(), method);
            }
            return method.invoke(state);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object rawCompound(ReadWriteNBT nbt) {
        try {
            Method method = nbt.getClass().getMethod("getCompound");
            method.setAccessible(true);
            return method.invoke(nbt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to unwrap NBT compound", e);
        }
    }

    private static int readNumericTag(
        @Nullable ReadableNBT nbt, @NotNull String key, int fallback
    ) {
        if (nbt == null || !nbt.hasTag(key)) {
            return fallback;
        }
        NBTType type = nbt.getType(key);
        if (type == NBTType.NBTTagShort) return nbt.getShort(key);
        if (type == NBTType.NBTTagByte) return nbt.getByte(key);
        if (type == NBTType.NBTTagLong) return (int) (long) nbt.getLong(key);
        return nbt.getInteger(key);
    }

    private FurnaceRootNbtBridge() {
        throw new UnsupportedOperationException();
    }
}
