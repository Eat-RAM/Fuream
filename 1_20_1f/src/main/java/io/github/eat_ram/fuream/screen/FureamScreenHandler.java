package io.github.eat_ram.fuream.screen;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.NotNull;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;

/**
 * The server-side screen handler of the virtual 9x6 furnace. It reports
 * {@link ScreenHandlerType#GENERIC_9X6} to the client (which then renders the
 * vanilla generic 9x6 screen) and enforces the interaction rules entirely on
 * the server:
 *
 * <ul>
 *   <li>bar and decor rows (fuel bar, progress bar, row 6) are inert;</li>
 *   <li>outputs are take-only;</li>
 *   <li>inputs accept only smeltable items, the fuel row only fuel;</li>
 *   <li>the bars/decor re-render each tick through
 *       {@link #sendContentUpdates()}.</li>
 * </ul>
 */
public class FureamScreenHandler extends GenericContainerScreenHandler {
    private final @NotNull AbstractFurnaceBlockEntity furnace;
    private final @NotNull FureamScreenInventory display;

    public FureamScreenHandler(
        int syncId, @NotNull PlayerInventory playerInventory,
        @NotNull AbstractFurnaceBlockEntity furnace
    ) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory,
              new FureamScreenInventory(furnace), 6);
        this.furnace = furnace;
        this.display = (FureamScreenInventory)this.getInventory();
    }

    @Override
    public void sendContentUpdates() {
        // Runs on the server once per tick for every open viewer.
        this.display.refreshVisuals();
        super.sendContentUpdates();
    }

    // ------------------------------------------------------------------
    //  Shift / double drag quick-move
    // ------------------------------------------------------------------

    @Override
    public @NotNull ItemStack
    quickMove(@NotNull PlayerEntity player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getStack();
        ItemStack originalCopy = original.copy();
        if (index >= FureamScreenInventory.PLAYER_START) {
            // Player inventory -> container rows by item type.
            if (FureamFurnaceLogic.isAcceptableInput(
                this.furnace.getWorld(), original, this.display.recipeType
            )) {
                int stop = this.display.getInputSlotStop();
                if (stop == 0 || !this.insertItem(original, 0, stop, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (FureamFurnaceLogic.isFuel(original)) { // no bucket
                int stop = this.display.getFuelSlotStop();
                if (stop == 18 ||
                    !this.insertItem(original, 18, stop, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            // Container -> player inventory.
            if (this.display.isBarOrDecorSlot(index)) {
                return ItemStack.EMPTY;
            }
            if (!this.insertItem(original, FureamScreenInventory.PLAYER_START,
                FureamScreenInventory.PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return originalCopy;
    }

    // ------------------------------------------------------------------
    //  Click handling
    // ------------------------------------------------------------------

    @Override
    public void onSlotClick(
        int slotIndex, int button, @NotNull SlotActionType actionType,
        @NotNull PlayerEntity player
    ) {
        if (slotIndex < 0 || slotIndex >= 54) {
            super.onSlotClick(slotIndex, button, actionType, player);
            return;
        }
        if (this.display.isFunctionalAreaSlot(slotIndex)) {
            int ca = this.display.getCurrentFunctionalArea();
            if (ca < this.display.functionalAreas.size()) {
                this.display.functionalAreas.get(ca).onGuiClick(
                    super::onSlotClick, this.display, slotIndex - 45, button,
                    actionType, player
                );
                return;
            }
        }
        if (this.display.isBarOrDecorSlot(slotIndex)) {
            switch (slotIndex) {
                case 51: {
                    int size = this.display.functionalAreas.size();
                    if (size > 0) {
                        int na = this.display.getCurrentFunctionalArea() + 1;
                        this.display
                        .setCurrentFunctionalArea(na < size ? na : 0);
                    }
                    break;
                }
                case 52:
                    this.display.switchPage(this.display.getPage() - 1);
                    break;
                case 53: {
                    int nextPage = this.display.getPage() + 1;
                    if (nextPage <= this.display.getLastPage()) {
                        this.display.switchPage(this.display.getPage() + 1);
                    }
                    break;
                }
            }
            return; // bars and decor are completely inert
        }
        if (this.display.isOutputSlot(slotIndex)) {
            this.onOutputSlotClick(slotIndex, button, actionType, player);
            return;
        }
        if (this.display.isInputSlot(slotIndex) ||
            this.display.isFuelRowSlot(slotIndex)) {
            this.onMaterialSlotClick(slotIndex, button, actionType, player);
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void onOutputSlotClick(
        int slotIndex, int button, @NotNull SlotActionType actionType,
        @NotNull PlayerEntity player
    ) {
        if (actionType == SlotActionType.QUICK_MOVE) {
            this.quickMove(player, slotIndex);
            return;
        }
        boolean cursorEmpty = this.getCursorStack().isEmpty();
        boolean taking = (actionType == SlotActionType.PICKUP && cursorEmpty)
                      || (actionType == SlotActionType.THROW && cursorEmpty);
        if (!taking) {
            // Every attempt to insert / swap / drag / clone into an output
            // slot is ignored.
            return;
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    private void onMaterialSlotClick(
        int slotIndex, int button, @NotNull SlotActionType actionType,
        @NotNull PlayerEntity player
    ) {
        boolean fuelRow = this.display.isFuelRowSlot(slotIndex);
        switch (actionType) {
            case SWAP: {
                ItemStack swapped = player.getInventory().getStack(button);
                if (!swapped.isEmpty() && fuelRow &&
                    !FureamFurnaceLogic.isFuel(swapped) &&
                    !swapped.isOf(Items.BUCKET)) {
                    return;
                }
                break;
            }
            case PICKUP:
            case QUICK_CRAFT: {
                ItemStack cursor = this.getCursorStack();
                if (!cursor.isEmpty() && fuelRow &&
                    !FureamFurnaceLogic.isFuel(cursor) &&
                    !cursor.isOf(Items.BUCKET)) {
                    return;
                }
                break;
            }
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public boolean
    canInsertIntoSlot(@NotNull ItemStack stack, @NotNull Slot slot) {
        if (!super.canInsertIntoSlot(stack, slot)) {
            return false;
        }
        int index = slot.id;
        if (index >= 0 && index < 54) {
            if (this.display.isFunctionalAreaSlot(index)) {
                int ca = this.display.getCurrentFunctionalArea();
                return ca < this.display.functionalAreas.size() &&
                       this.display.functionalAreas.get(ca)
                       .isStorable(this.display, index - 45);
            }
            if (this.display.isBarOrDecorSlot(index) ||
                this.display.isOutputSlot(index)) {
                return false;
            }
            if (this.display.isFuelRowSlot(index)) {
                return FureamFurnaceLogic.isFuel(stack);
            }
        }
        return true;
    }
}
