package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;

import org.bukkit.block.Furnace;

public final class FurnaceCompat {
    public static int getBurnTime(Furnace furnace) {
        return getInt(furnace, "getBurnTime", 0);
    }

    public static int getCookTime(Furnace furnace) {
        return getInt(furnace, "getCookTime", 0);
    }

    public static int getCookTimeTotal(Furnace furnace, int fallback) {
        return getInt(furnace, "getCookTimeTotal", fallback);
    }

    public static void setCookTimeTotal(Furnace furnace, int value) {
        invokeNumberSetter(furnace, "setCookTimeTotal", value);
    }

    public static void setCookTime(Furnace furnace, int value) {
        invokeNumberSetter(furnace, "setCookTime", value);
    }

    public static void setBurnTime(Furnace furnace, int value) {
        invokeNumberSetter(furnace, "setBurnTime", value);
    }

    private static int getInt(Object target, String name, int fallback) {
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static void invokeNumberSetter(Object target, String name, int value) {
        for (Method method : target.getClass().getMethods()) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            try {
                if (type == short.class || type == Short.class) method.invoke(target, (short) value);
                else if (type == int.class || type == Integer.class) method.invoke(target, value);
                else continue;
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private FurnaceCompat() {
        throw new UnsupportedOperationException();
    }
}
