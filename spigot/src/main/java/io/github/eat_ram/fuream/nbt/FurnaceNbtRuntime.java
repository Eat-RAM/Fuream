package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pure-JDK state used by instrumented NMS classes. A second copy of this class
 * is injected into the bootstrap class loader, so it must not reference Bukkit or
 * plugin classes.
 */
public final class FurnaceNbtRuntime {
    private static final Map<Object, Object[]> STATES =
        Collections.synchronizedMap(new WeakHashMap<Object, Object[]>());
    private static final Map<Class<?>, Field> TAG_FIELDS =
        Collections.synchronizedMap(new WeakHashMap<Class<?>, Field>());
    private static final Map<Class<?>, Method> SHORT_SETTERS =
        Collections.synchronizedMap(new WeakHashMap<Class<?>, Method>());
    private static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<Boolean>();
    private static volatile boolean installed;
    private static volatile long failureCount;
    private static volatile String lastFailure = "";

    public static void capture(Object tileEntity, Object rootNbt, String dataKey) {
        try {
            Object data = tags(rootNbt).get(dataKey);
            STATES.put(tileEntity, new Object[] {data, rootNbt});
        } catch (Throwable failure) {
            recordFailure(failure);
        }
    }

    public static void inject(Object tileEntity, Object rootNbt, String dataKey) {
        if (Boolean.TRUE.equals(SUPPRESSED.get())) return;
        try {
            Object[] stored = STATES.get(tileEntity);
            if (stored == null || stored.length == 0) return;
            if (stored[0] == null) tags(rootNbt).remove(dataKey);
            else tags(rootNbt).put(dataKey, stored[0]);
            if (stored.length >= 4 && stored[1] instanceof Integer) {
                setShort(rootNbt, "BurnTime", ((Integer) stored[1]).shortValue());
                setShort(rootNbt, "CookTime", ((Integer) stored[2]).shortValue());
                setShort(rootNbt, "CookTimeTotal", ((Integer) stored[3]).shortValue());
            }
        } catch (Throwable failure) {
            recordFailure(failure);
        }
    }

    public static Object[] getState(Object tileEntity) {
        return STATES.get(tileEntity);
    }

    public static void putState(Object tileEntity, Object[] state) {
        if (tileEntity != null && state != null) STATES.put(tileEntity, state);
    }

    public static void setSuppressed(boolean suppressed) {
        if (suppressed) SUPPRESSED.set(Boolean.TRUE);
        else SUPPRESSED.remove();
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static void markInstalled() {
        installed = true;
    }

    public static long getFailureCount() {
        return failureCount;
    }

    public static String getLastFailure() {
        return lastFailure;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tags(Object compound) throws ReflectiveOperationException {
        Class<?> type = compound.getClass();
        Field field = TAG_FIELDS.get(type);
        if (field == null) {
            field = findTagField(type);
            TAG_FIELDS.put(type, field);
        }
        return (Map<String, Object>) field.get(compound);
    }

    private static Field findTagField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("NBT tag map on " + type.getName());
    }

    private static void setShort(Object compound, String key, short value)
        throws ReflectiveOperationException {
        Class<?> type = compound.getClass();
        Method setter = SHORT_SETTERS.get(type);
        if (setter == null) {
            setter = findShortSetter(type);
            SHORT_SETTERS.put(type, setter);
        }
        setter.invoke(compound, key, value);
    }

    private static Method findShortSetter(Class<?> type) throws NoSuchMethodException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) && parameters.length == 2 &&
                    parameters[0] == String.class && parameters[1] == short.class &&
                    method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("NBT short setter on " + type.getName());
    }

    private static void recordFailure(Throwable failure) {
        failureCount++;
        lastFailure = failure.getClass().getName() + ": " + String.valueOf(failure.getMessage());
    }

    private FurnaceNbtRuntime() {
    }
}
