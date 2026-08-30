package io.github.eat_ram.fuream.util;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.nbt.NativeItemNbt;
import io.github.eat_ram.fuream.nbt.NativeNbtCompound;
import io.github.eat_ram.fuream.nbt.NativeNbtList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NbtUtil {
    public static final int MAX_LANE_SLOTS = 576;
    private static final Map<Class<?>, Field> MAP_FIELDS = new ConcurrentHashMap<>();
    public static @NotNull ItemStack fromNbt(@Nullable NativeNbtCompound compound) {
        return compound == null ? ItemCompat.empty() : NativeItemNbt.read(compound);
    }

    public static void writeItemToCompound(@NotNull NativeNbtCompound target, @Nullable ItemStack stack) {
        if (!ItemCompat.isEmpty(stack)) copyTags(NativeItemNbt.write(stack), target);
    }

    public static void writeStacks(
        @NotNull NativeNbtCompound parent, @NotNull String key,
        @NotNull Iterable<@NotNull ItemStack> stacks
    ) {
        NativeNbtList list = resetCompoundList(parent, key);
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (slot >= MAX_LANE_SLOTS) {
                throw new IllegalArgumentException(
                    "Lane " + key + " exceeds " + MAX_LANE_SLOTS + " slots"
                );
            }
            if (!ItemCompat.isEmpty(stack)) {
                NativeNbtCompound sub = NativeItemNbt.write(stack);
                sub.setInt("Slot", slot);
                list.add(sub);
            }
            slot++;
        }
    }

    public static @NotNull NativeNbtList resetCompoundList(
        @NotNull NativeNbtCompound parent, @NotNull String key
    ) {
        return parent.resetList(key);
    }

    public static void readStacks(
        @NotNull NativeNbtCompound parent, @NotNull String key,
        @NotNull List<ItemStack> target
    ) {
        NativeNbtList list = parent.getList(key);
        if (list == null) return;
        if (list.size() > MAX_LANE_SLOTS) {
            throw new IllegalArgumentException("NBT list " + key + " exceeds " + MAX_LANE_SLOTS + " entries");
        }
        for (int i = 0; i < list.size(); i++) {
            NativeNbtCompound sub = list.getCompound(i);
            if (sub == null || !sub.has("Slot")) continue;
            int slot = sub.getInt("Slot", -1);
            if (slot < 0 || slot >= MAX_LANE_SLOTS) {
                throw new IllegalArgumentException(
                    "NBT slot " + slot + " is outside 0.." + (MAX_LANE_SLOTS - 1)
                );
            }
            ItemStack stack = fromNbt(sub);
            if (ItemCompat.isEmpty(stack)) continue;
            while (target.size() <= slot) target.add(ItemCompat.empty());
            target.set(slot, stack);
        }
    }

    private static void copyTags(NativeNbtCompound source, NativeNbtCompound target) {
        try {
            Field sourceField = findMapField(source.raw().getClass());
            Field targetField = findMapField(target.raw().getClass());
            @SuppressWarnings("unchecked")
            Map<String, Object> from = (Map<String, Object>) sourceField.get(source.raw());
            @SuppressWarnings("unchecked")
            Map<String, Object> to = (Map<String, Object>) targetField.get(target.raw());
            to.putAll(from);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to copy native item NBT", e);
        }
    }

    private static Field findMapField(Class<?> type) {
        Field cached = MAP_FIELDS.get(type);
        if (cached != null) return cached;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    MAP_FIELDS.put(type, field);
                    return field;
                }
            }
        }
        throw new IllegalStateException("NBT map field missing on " + type.getName());
    }

    private NbtUtil() {
    }
}
