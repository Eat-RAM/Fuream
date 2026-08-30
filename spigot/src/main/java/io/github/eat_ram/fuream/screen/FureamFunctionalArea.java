package io.github.eat_ram.fuream.screen;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public interface FureamFunctionalArea {
    @NotNull ItemStack getGuiStack(FureamScreenInventory inv, int slot);

    void setGuiStack(FureamScreenInventory inv, int slot, ItemStack newStack);

    void onGuiClick(
        SlotClick slotClick, FureamScreenInventory display, int slot,
        int button, ClickType clickType, Player player
    );

    boolean isStorable(FureamScreenInventory inv, int slot);

    boolean isTakable(FureamScreenInventory inv, int slot);

    void refreshVisuals(FureamScreenInventory inv);

    @FunctionalInterface
    interface SlotClick {
        void onSlotClick(
            int slotIndex, int button, ClickType clickType,
            Player player
        );
    }
}
