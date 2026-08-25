package io.github.eat_ram.fuream.screen;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.NotNull;

public interface FureamFunctionalArea {
    @NotNull ItemStack getGuiStack(FureamScreenInventory inv, int slot);

    void setGuiStack(FureamScreenInventory inv, int slot, ItemStack newStack);

    void onGuiClick(
        SlotClick slotClick, FureamScreenInventory display, int slot,
        int button, SlotActionType actionType, PlayerEntity player
    );

    boolean isStorable(FureamScreenInventory inv, int slot);

    boolean isTakable(FureamScreenInventory inv, int slot);

    void refreshVisuals(FureamScreenInventory inv);

    @FunctionalInterface
    public static interface SlotClick {
        void onSlotClick(
            int slotIndex, int button, SlotActionType actionType,
            PlayerEntity player
        );
    }
}
