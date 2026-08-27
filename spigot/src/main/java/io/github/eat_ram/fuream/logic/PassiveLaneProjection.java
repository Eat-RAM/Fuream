package io.github.eat_ram.fuream.logic;

import java.util.List;

import io.github.eat_ram.fuream.compat.ItemCompat;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Maintains one native furnace slot as a view over a virtual item lane. */
public final class PassiveLaneProjection {
    public enum SelectionMode {
        FIRST_PRESENT,
        FUEL_THEN_PRESENT
    }

    public static final class State {
        private int sourceSlot = -1;
        private boolean exhausted;
        private boolean preferredFuelExhausted;
        private boolean preferredFuelSwitchBlocked;

        public int getSourceSlot() {
            return this.sourceSlot;
        }

        public boolean isExhausted() {
            return this.exhausted;
        }

        public boolean isPreferredFuelExhausted() {
            return this.preferredFuelExhausted;
        }

        public void blockPreferredFuelSwitch() {
            this.preferredFuelSwitchBlocked = true;
        }

        public void reset() {
            this.sourceSlot = -1;
            this.exhausted = false;
            this.preferredFuelExhausted = false;
            this.preferredFuelSwitchBlocked = false;
        }
    }

    public static @Nullable ItemStack begin(
        @NotNull List<ItemStack> virtual, @Nullable ItemStack nativeStack,
        @NotNull SelectionMode mode, @NotNull State state
    ) {
        state.reset();
        if (!ItemCompat.isEmpty(nativeStack)) {
            return nativeStack;
        }
        return takeNext(virtual, mode, state);
    }

    public static @Nullable ItemStack takeNext(
        @NotNull List<ItemStack> virtual, @NotNull SelectionMode mode,
        @NotNull State state
    ) {
        state.sourceSlot = -1;
        state.preferredFuelSwitchBlocked = false;
        if (state.exhausted) {
            return null;
        }

        int selected = selectSlot(virtual, mode, state);
        if (selected < 0) {
            state.exhausted = true;
            return null;
        }

        ItemStack projected = virtual.get(selected).clone();
        virtual.set(selected, ItemCompat.empty());
        state.sourceSlot = selected;
        return projected;
    }

    public static boolean hasPreferredFuel(
        @NotNull List<ItemStack> virtual, @NotNull State state
    ) {
        if (state.preferredFuelExhausted || state.preferredFuelSwitchBlocked) {
            return false;
        }
        if (firstFuel(virtual) >= 0) {
            return true;
        }
        state.preferredFuelExhausted = true;
        return false;
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
            state.exhausted = false;
            if (FuelTable.isFuel(nativeStack)) {
                state.preferredFuelExhausted = false;
            }
            return null;
        }

        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(
            virtual, nativeStack, Math.max(1, virtual.size())
        );
        state.sourceSlot = -1;
        if (ItemCompat.isEmpty(remainder) || remainder.getAmount() < nativeStack.getAmount()) {
            state.exhausted = false;
            if (FuelTable.isFuel(nativeStack)) {
                state.preferredFuelExhausted = false;
            }
        }
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static int selectSlot(
        @NotNull List<ItemStack> virtual, @NotNull SelectionMode mode,
        @NotNull State state
    ) {
        if (mode == SelectionMode.FUEL_THEN_PRESENT && !state.preferredFuelExhausted) {
            int fuel = firstFuel(virtual);
            if (fuel >= 0) {
                return fuel;
            }
            state.preferredFuelExhausted = true;
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
        throw new UnsupportedOperationException();
    }
}
