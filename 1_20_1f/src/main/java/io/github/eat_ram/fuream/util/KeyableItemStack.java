package io.github.eat_ram.fuream.util;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class KeyableItemStack {
    public final @NotNull ItemStack stack;

    public KeyableItemStack(@Nullable ItemStack stack) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof KeyableItemStack)) {
            return false;
        }
        KeyableItemStack that = (KeyableItemStack)obj;
        return this.stack.isEmpty() ? that.stack.isEmpty() :
               this.stack.getItem() == that.stack.getItem() &&
               this.stack.getCount() == that.stack.getCount() &&
               Objects.equals(this.stack.getNbt(), that.stack.getNbt());
    }

    @Override
    public int hashCode() {
        return this.stack.isEmpty() ? 0 : Objects.hash(
            this.stack.getItem(), this.stack.getCount(), this.stack.getNbt()
        );
    }
}
