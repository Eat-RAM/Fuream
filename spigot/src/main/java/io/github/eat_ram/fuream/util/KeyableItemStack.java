package io.github.eat_ram.fuream.util;

import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KeyableItemStack {
    public final @NotNull ItemStack stack;

    public KeyableItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
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
        if (this.stack.getType().isAir()) {
            return that.stack.getType().isAir();
        }
        if (that.stack.getType().isAir()) {
            return false;
        }
        return this.stack.getType() == that.stack.getType() &&
               this.stack.getAmount() == that.stack.getAmount() &&
               Objects.equals(this.stack.getItemMeta(), that.stack.getItemMeta());
    }

    @Override
    public int hashCode() {
        if (this.stack.getType().isAir()) {
            return 0;
        }
        ItemMeta meta = this.stack.getItemMeta();
        return Objects.hash(this.stack.getType(), this.stack.getAmount(), meta != null ? meta.hashCode() : 0);
    }
}
