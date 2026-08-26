package io.github.eat_ram.fuream.util;

import java.util.List;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import de.tr7zw.nbtapi.iface.ReadableNBTList;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NbtUtil {
    public static @NotNull ItemStack fromNbt(@Nullable ReadableNBT compound) {
        if (compound == null || compound.getKeys().isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        try {
            ItemStack stack = NBT.itemStackFromNBT(compound);
            return stack != null ? stack : new ItemStack(Material.AIR);
        } catch (Exception e) {
            return new ItemStack(Material.AIR);
        }
    }

    public static void writeItemToCompound(@NotNull ReadWriteNBT target, @Nullable ItemStack stack) {
        if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
            ReadWriteNBT container = NBT.itemStackToNBT(stack);
            target.mergeCompound(container);
        }
    }

    public static void writeStacks(
        @NotNull ReadWriteNBT parent, @NotNull String key,
        @NotNull Iterable<@NotNull ItemStack> stacks
    ) {
        ReadWriteNBTCompoundList list = resetCompoundList(parent, key);
        int slot = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                ReadWriteNBT sub = list.addCompound();
                ReadWriteNBT itemNbt = NBT.itemStackToNBT(stack);
                sub.mergeCompound(itemNbt);
                sub.setInteger("Slot", slot);
            }
            ++slot;
        }
    }

    public static @NotNull ReadWriteNBTCompoundList resetCompoundList(
        @NotNull ReadWriteNBT parent, @NotNull String key
    ) {
        if (parent.hasTag(key)) {
            parent.removeKey(key);
        }
        // NBT-API creates compound-list tags lazily. Merging an explicit empty
        // list keeps Fabric's required `Key: []` representation even when no
        // entries are added afterwards.
        parent.mergeCompound(NBT.parseNBT("{\"" + key + "\":[]}"));
        return parent.getCompoundList(key);
    }

    public static void readStacks(
        @NotNull ReadableNBT parent, @NotNull String key,
        @NotNull List<ItemStack> target
    ) {
        if (!parent.hasTag(key)) {
            return;
        }
        ReadableNBTList<ReadWriteNBT> list = parent.getCompoundList(key);
        for (int i = 0; i < list.size(); ++i) {
            ReadWriteNBT sub = list.get(i);
            if (sub.hasTag("Slot")) {
                int slot = sub.getInteger("Slot");
                ItemStack stack = fromNbt(sub);
                if (!stack.getType().isAir() && slot >= 0) {
                    if (slot < target.size()) {
                        target.set(slot, stack);
                    } else {
                        while (target.size() <= slot) {
                            target.add(new ItemStack(Material.AIR));
                        }
                        target.set(slot, stack);
                    }
                }
            }
        }
    }

    private NbtUtil() {
        throw new UnsupportedOperationException();
    }
}
