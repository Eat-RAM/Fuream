package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NativeNbtList {
    private static final Map<Class<?>, Field> LIST_FIELDS = new ConcurrentHashMap<>();
    private final Object raw;

    NativeNbtList(Object raw) {
        this.raw = raw;
    }

    public int size() {
        return values().size();
    }

    public NativeNbtCompound getCompound(int index) {
        Object value = values().get(index);
        return value == null ? null : new NativeNbtCompound(value);
    }

    public void add(NativeNbtCompound compound) {
        if (raw instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) raw;
            list.add(compound.raw());
            return;
        }
        for (java.lang.reflect.Method method : raw.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 1 && parameters[0].isInstance(compound.raw())) {
                try {
                    method.invoke(raw, compound.raw());
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        throw new IllegalStateException("Unable to append to NBT list " + raw.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private List<Object> values() {
        try {
            Field field = LIST_FIELDS.get(raw.getClass());
            if (field == null) {
                field = findListField(raw.getClass());
                LIST_FIELDS.put(raw.getClass(), field);
            }
            return (List<Object>) field.get(raw);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to access NBT list", e);
        }
    }

    private static Field findListField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new IllegalStateException("Unable to locate NBT list values on " + type.getName());
    }
}
