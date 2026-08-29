package io.github.eat_ram.fuream.logic;

import java.util.List;

import io.github.eat_ram.fuream.compat.ItemCompat;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Moves one virtual stack into a native furnace slot while Fuream is disabled. */
public final class PassiveLaneProjection {
    public enum SelectionMode {
        FIRST_PRESENT,
        FUEL_THEN_PRESENT
    }

    public static final class State {
        private int sourceSlot = -1;
        private ItemStack published;

        public int getSourceSlot() {
            return this.sourceSlot;
        }

        public @Nullable ItemStack getPublished() {
            return ItemCompat.isEmpty(this.published) ? null : this.published.clone();
        }

        public void reset() {
            this.sourceSlot = -1;
            this.published = null;
        }
    }

    /**
     * Projects at most one stack. Callers must retain the projected state and
     * must not call this again until Fuream is re-enabled for the furnace.
     */
    public static @Nullable ItemStack begin(
        @NotNull List<ItemStack> virtual, @Nullable ItemStack nativeStack,
        @NotNull SelectionMode mode, @NotNull State state
    ) {
        state.reset();
        if (!ItemCompat.isEmpty(nativeStack)) {
            return nativeStack;
        }
        int selected = selectSlot(virtual, mode);
        if (selected < 0) {
            return null;
        }

        ItemStack projected = virtual.get(selected).clone();
        virtual.set(selected, ItemCompat.empty());
        state.sourceSlot = selected;
        state.published = projected.clone();
        return projected;
    }

    /**
     * Returns the native stack to its source slot when possible. Any remainder
     * stays native so callers can retry without losing items.
     */
    public static @Nullable ItemStack restore(
        @NotNull List<ItemStack> virtual, @Nullable ItemStack nativeStack,
        @NotNull State state
    ) {
        if (ItemCompat.isEmpty(nativeStack)) {
            state.sourceSlot = -1;
            return null;
        }

        int source = state.sourceSlot;
        if (source >= 0 && source < virtual.size() && ItemCompat.isEmpty(virtual.get(source))) {
            virtual.set(source, nativeStack.clone());
            state.sourceSlot = -1;
            return null;
        }

        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(
            virtual, nativeStack, Math.max(1, virtual.size())
        );
        state.sourceSlot = -1;
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static int selectSlot(
        @NotNull List<ItemStack> virtual, @NotNull SelectionMode mode
    ) {
        if (mode == SelectionMode.FUEL_THEN_PRESENT) {
            int fuel = firstFuel(virtual);
            if (fuel >= 0) {
                return fuel;
            }
        }
        return firstPresent(virtual);
    }

    private static int firstFuel(@NotNull List<ItemStack> virtual) {
        for (int i = 0; i < virtual.size(); i++) {
            ItemStack stack = virtual.get(i);
            if (!ItemCompat.isEmpty(stack) && FuelTable.isFuel(stack)) {
                return i;
            }
        }
        return -1;
    }

    private static int firstPresent(@NotNull List<ItemStack> virtual) {
        for (int i = 0; i < virtual.size(); i++) {
            if (!ItemCompat.isEmpty(virtual.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private PassiveLaneProjection() {
    }
}
