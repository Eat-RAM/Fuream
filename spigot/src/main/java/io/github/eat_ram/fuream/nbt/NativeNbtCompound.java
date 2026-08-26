package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;

/** Minimal reflective NBT view shared by legacy, flattened and component servers. */
public final class NativeNbtCompound {
    private static final Map<Class<?>, Field> MAP_FIELDS = new ConcurrentHashMap<>();
    private static final Map<String, Method> SETTERS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> LIST_CLASSES = new ConcurrentHashMap<>();

    private final Object raw;

    public NativeNbtCompound(Object raw) {
        if (raw == null) throw new IllegalArgumentException("raw NBT compound cannot be null");
        this.raw = raw;
    }

    public Object raw() {
        return raw;
    }

    public static NativeNbtCompound create(Class<?> compoundClass) {
        try {
            Constructor<?> constructor = compoundClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return new NativeNbtCompound(constructor.newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create " + compoundClass.getName(), e);
        }
    }

    public static NativeNbtCompound createForServer() {
        ClassLoader loader = Bukkit.getServer().getClass().getClassLoader();
        String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
        String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
        String[] candidates = {
            "net.minecraft.nbt.CompoundTag",
            "net.minecraft.nbt.NBTTagCompound",
            "net.minecraft.server." + version + ".NBTTagCompound"
        };
        for (String candidate : candidates) {
            try {
                return create(Class.forName(candidate, false, loader));
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new IllegalStateException("Unable to locate the server NBT compound class");
    }

    public boolean has(String key) {
        return tags().containsKey(key);
    }

    public void remove(String key) {
        tags().remove(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(tags().keySet());
    }

    public NativeNbtCompound getCompound(String key) {
        Object value = tags().get(key);
        return isCompound(value) ? new NativeNbtCompound(value) : null;
    }

    public NativeNbtCompound getOrCreateCompound(String key) {
        NativeNbtCompound existing = getCompound(key);
        if (existing != null) return existing;
        NativeNbtCompound created = create(raw.getClass());
        tags().put(key, created.raw());
        return created;
    }

    public NativeNbtList getList(String key) {
        Object value = tags().get(key);
        return value == null ? null : new NativeNbtList(value);
    }

    public NativeNbtList resetList(String key) {
        Class<?> listClass = LIST_CLASSES.get(raw.getClass());
        if (listClass == null) {
            listClass = locateListClass(raw.getClass());
            LIST_CLASSES.put(raw.getClass(), listClass);
        }
        try {
            Constructor<?> constructor = listClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object list = constructor.newInstance();
            tags().put(key, list);
            return new NativeNbtList(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to create NBT list", e);
        }
    }

    public void setString(String key, String value) {
        invokeSetter(String.class, key, value);
    }

    public void setInt(String key, int value) {
        invokeSetter(int.class, key, value);
    }

    public void setFloat(String key, float value) {
        invokeSetter(float.class, key, value);
    }

    public void setShort(String key, short value) {
        invokeSetter(short.class, key, value);
    }

    public String getString(String key) {
        Object value = primitiveValue(tags().get(key));
        return value instanceof String ? (String) value : "";
    }

    public int getInt(String key, int fallback) {
        Object value = primitiveValue(tags().get(key));
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    public float getFloat(String key, float fallback) {
        Object value = primitiveValue(tags().get(key));
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tags() {
        try {
            Field field = MAP_FIELDS.get(raw.getClass());
            if (field == null) {
                field = findMapField(raw.getClass());
                MAP_FIELDS.put(raw.getClass(), field);
            }
            return (Map<String, Object>) field.get(raw);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access NBT compound", e);
        }
    }

    private void invokeSetter(Class<?> valueType, String key, Object value) {
        String cacheKey = raw.getClass().getName() + '#' + valueType.getName();
        Method method = SETTERS.get(cacheKey);
        if (method == null) {
            method = findSetter(raw.getClass(), valueType);
            SETTERS.put(cacheKey, method);
        }
        try {
            method.invoke(raw, key, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to write NBT value " + key, e);
        }
    }

    private static Method findSetter(Class<?> type, Class<?> valueType) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) && parameters.length == 2 &&
                    parameters[0] == String.class && parameters[1] == valueType &&
                    method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException("Unable to locate NBT setter for " + valueType.getName());
    }

    private static Field findMapField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new IllegalStateException("Unable to locate NBT compound map on " + type.getName());
    }

    private static Class<?> locateListClass(Class<?> compoundClass) {
        String packageName = compoundClass.getPackage().getName();
        ClassLoader loader = compoundClass.getClassLoader();
        String[] candidates = {
            packageName + ".ListTag",
            packageName + ".NBTTagList"
        };
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new IllegalStateException("Unable to locate NBT list next to " + compoundClass.getName());
    }

    private static boolean isCompound(Object value) {
        if (value == null) return false;
        String name = value.getClass().getSimpleName();
        return name.equals("CompoundTag") || name.equals("NBTTagCompound");
    }

    private static Object primitiveValue(Object tag) {
        if (tag == null) return null;
        for (Class<?> current = tag.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                Class<?> type = field.getType();
                if (type == String.class || type.isPrimitive() || Number.class.isAssignableFrom(type)) {
                    try {
                        field.setAccessible(true);
                        return field.get(tag);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
        }
        return null;
    }
}
