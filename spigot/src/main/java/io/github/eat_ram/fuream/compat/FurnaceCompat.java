package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.block.Furnace;

public final class FurnaceCompat {
    private static final Map<Class<?>, Accessors> ACCESSORS = new ConcurrentHashMap<>();

    public static int getBurnTime(Furnace furnace) {
        Accessors accessors = accessors(furnace);
        return accessors.getInt(furnace, accessors.getBurnTime, 0);
    }

    public static int getCookTime(Furnace furnace) {
        Accessors accessors = accessors(furnace);
        return accessors.getInt(furnace, accessors.getCookTime, 0);
    }

    public static int getCookTimeTotal(Furnace furnace, int fallback) {
        Accessors accessors = accessors(furnace);
        return accessors.getInt(furnace, accessors.getCookTimeTotal, fallback);
    }

    public static void setCookTimeTotal(Furnace furnace, int value) {
        Accessors accessors = accessors(furnace);
        accessors.setInt(furnace, accessors.setCookTimeTotal, value);
    }

    public static void setCookTime(Furnace furnace, int value) {
        Accessors accessors = accessors(furnace);
        accessors.setInt(furnace, accessors.setCookTime, value);
    }

    public static void setBurnTime(Furnace furnace, int value) {
        Accessors accessors = accessors(furnace);
        accessors.setInt(furnace, accessors.setBurnTime, value);
    }

    public static void initialize(Furnace furnace) {
        accessors(furnace);
    }

    private static Accessors accessors(Furnace furnace) {
        Class<?> type = furnace.getClass();
        Accessors accessors = ACCESSORS.get(type);
        if (accessors != null) return accessors;
        Accessors created = new Accessors(type);
        Accessors existing = ACCESSORS.putIfAbsent(type, created);
        return existing == null ? created : existing;
    }

    private static final class Accessors {
        private final Method getBurnTime;
        private final Method getCookTime;
        private final Method getCookTimeTotal;
        private final Method setBurnTime;
        private final Method setCookTime;
        private final Method setCookTimeTotal;

        private Accessors(Class<?> type) {
            this.getBurnTime = findGetter(type, "getBurnTime");
            this.getCookTime = findGetter(type, "getCookTime");
            this.getCookTimeTotal = findGetter(type, "getCookTimeTotal");
            this.setBurnTime = findSetter(type, "setBurnTime");
            this.setCookTime = findSetter(type, "setCookTime");
            this.setCookTimeTotal = findSetter(type, "setCookTimeTotal");
        }

        private int getInt(Object target, Method method, int fallback) {
            if (method == null) return fallback;
            try {
                Object value = method.invoke(target);
                return value instanceof Number ? ((Number) value).intValue() : fallback;
            } catch (ReflectiveOperationException ignored) {
                return fallback;
            }
        }

        private void setInt(Object target, Method method, int value) {
            if (method == null) return;
            Class<?> type = method.getParameterTypes()[0];
            try {
                Object argument;
                if (type == short.class || type == Short.class) {
                    argument = Short.valueOf((short) value);
                } else {
                    argument = Integer.valueOf(value);
                }
                method.invoke(target, argument);
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private static Method findGetter(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private static Method findSetter(Class<?> type, String name) {
            for (Method method : type.getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterTypes().length != 1) continue;
                Class<?> parameter = method.getParameterTypes()[0];
                if (parameter == short.class || parameter == Short.class ||
                    parameter == int.class || parameter == Integer.class) return method;
            }
            return null;
        }
    }

    private FurnaceCompat() {
    }
}
