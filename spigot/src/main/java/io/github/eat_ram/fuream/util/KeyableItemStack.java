package io.github.eat_ram.fuream.util;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import io.github.eat_ram.fuream.compat.ItemCompat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KeyableItemStack {
    public final @NotNull ItemStack stack;

    public KeyableItemStack(@Nullable ItemStack stack) {
        if (ItemCompat.isEmpty(stack)) {
            this.stack = new ItemStack(Material.AIR);
        } else {
            this.stack = stack.clone();
            this.stack.setAmount(1);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyableItemStack)) {
            return false;
        }
        KeyableItemStack that = (KeyableItemStack) obj;
        if (ItemCompat.isEmpty(this.stack)) {
            return ItemCompat.isEmpty(that.stack);
        }
        if (ItemCompat.isEmpty(that.stack)) {
            return false;
        }
        return this.stack.getType() == that.stack.getType() &&
               this.stack.getDurability() == that.stack.getDurability() &&
               this.stack.getAmount() == that.stack.getAmount() &&
               Objects.equals(this.stack.getItemMeta(), that.stack.getItemMeta());
    }

    @Override
    public int hashCode() {
        if (ItemCompat.isEmpty(this.stack)) {
            return 0;
        }
        ItemMeta meta = this.stack.getItemMeta();
        return Objects.hash(
            this.stack.getType(), this.stack.getDurability(), this.stack.getAmount(),
            meta != null ? meta.hashCode() : 0
        );
    }
}
