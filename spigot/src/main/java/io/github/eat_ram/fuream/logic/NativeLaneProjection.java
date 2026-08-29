package io.github.eat_ram.fuream.logic;

import java.util.List;

import io.github.eat_ram.fuream.compat.ItemCompat;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Publishes one virtual stack into a native furnace slot and merges the observed result back. */
public final class NativeLaneProjection {
    public enum SelectionMode {
        FIRST_PRESENT,
        BUCKET_REMAINDER
    }

    public static final class State {
        private int sourceSlot = -1;
        private ItemStack published;

        public int getSourceSlot() {
            return this.sourceSlot;
        }

        public boolean isActive() {
            return this.sourceSlot >= 0;
        }

        public @Nullable ItemStack getPublished() {
            return ItemCompat.isEmpty(this.published) ? null : this.published.clone();
        }

        public void reset() {
            this.sourceSlot = -1;
            this.published = null;
        }

        public void restore(int sourceSlot, @Nullable ItemStack published) {
            this.sourceSlot = sourceSlot;
            this.published = ItemCompat.isEmpty(published) ? null : published.clone();
        }
    }

    public static @Nullable ItemStack publish(
        @NotNull List<ItemStack> virtual, @Nullable ItemStack nativeStack,
        @NotNull SelectionMode mode, @NotNull State state
    ) {
        state.reset();
        if (!ItemCompat.isEmpty(nativeStack)) {
            return nativeStack.clone();
        }
        int selected = selectSlot(virtual, mode);
        if (selected < 0) {
            return null;
        }
        ItemStack projected = virtual.get(selected).clone();
        state.sourceSlot = selected;
        state.published = projected.clone();
        return projected;
    }

    public static @Nullable ItemStack reconcile(
        @NotNull List<ItemStack> virtual, @Nullable ItemStack nativeStack,
        int maxSlots, @NotNull State state
    ) {
        if (!state.isActive()) {
            return nativeStack;
        }
        int source = state.sourceSlot;
        ItemStack published = state.published;
        state.reset();
        if (source >= 0 && source < maxSlots && source < virtual.size() &&
            sameStack(virtual.get(source), published)) {
            virtual.set(source, ItemCompat.isEmpty(nativeStack) ? ItemCompat.empty() : nativeStack.clone());
            return null;
        }

        removePublished(virtual, published, maxSlots);
        if (ItemCompat.isEmpty(nativeStack)) return null;
        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(
            virtual, nativeStack.clone(), maxSlots
        );
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static void removePublished(List<ItemStack> virtual, ItemStack published, int maxSlots) {
        if (ItemCompat.isEmpty(published)) return;
        int remaining = published.getAmount();
        for (int i = 0; i < Math.min(maxSlots, virtual.size()) && remaining > 0; i++) {
            ItemStack current = virtual.get(i);
            if (ItemCompat.isEmpty(current) || !current.isSimilar(published)) continue;
            int remove = Math.min(remaining, current.getAmount());
            current.setAmount(current.getAmount() - remove);
            remaining -= remove;
            if (current.getAmount() <= 0) virtual.set(i, ItemCompat.empty());
        }
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return ItemCompat.isEmpty(left) && ItemCompat.isEmpty(right) ||
            !ItemCompat.isEmpty(left) && !ItemCompat.isEmpty(right) &&
                left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static int selectSlot(List<ItemStack> virtual, SelectionMode mode) {
        for (int i = 0; i < virtual.size(); i++) {
            ItemStack stack = virtual.get(i);
            if (ItemCompat.isEmpty(stack)) continue;
            if (mode == SelectionMode.FIRST_PRESENT || isBucket(stack)) return i;
        }
        return -1;
    }

    private static boolean isBucket(ItemStack stack) {
        String name = stack.getType().name();
        return "BUCKET".equals(name) || "WATER_BUCKET".equals(name);
    }

    private NativeLaneProjection() {
    }
}
