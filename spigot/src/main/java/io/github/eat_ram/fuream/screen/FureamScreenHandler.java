package io.github.eat_ram.fuream.screen;

import java.util.Map;

import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class FureamScreenHandler {
    private final @NotNull FureamScreenInventory display;

    public FureamScreenHandler(@NotNull FureamScreenInventory display) {
        this.display = display;
    }

    public void handleClick(InventoryClickEvent event, Player player) {
        int rawSlot = event.getRawSlot();
        ClickType clickType = event.getClick();
        InventoryAction action = event.getAction();

        if (rawSlot < 0) {
            return;
        }

        // Click in player inventory with shift-click into custom GUI
        if (rawSlot >= 54) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (current != null && !current.getType().isAir()) {
                    ItemStack remaining = this.quickMoveToContainer(current);
                    event.setCurrentItem(remaining.getType().isAir() ? null : remaining);
                    this.display.syncToBukkitInventory();
                }
            }
            return;
        }

        // Click is within the 54-slot furnace GUI
        event.setCancelled(true);

        if (this.display.isFunctionalAreaSlot(rawSlot)) {
            int ca = this.display.getCurrentFunctionalArea();
            if (ca < this.display.functionalAreas.size()) {
                FureamFunctionalArea area = this.display.functionalAreas.get(ca);
                area.onGuiClick(
                    (sIdx, btn, ct, p) -> this.handleFunctionalSlotDefault(rawSlot, sIdx, event, p),
                    this.display, rawSlot - 45, event.getHotbarButton(), clickType, player
                );
                this.display.syncToBukkitInventory();
            }
            return;
        }

        if (this.display.isBarOrDecorSlot(rawSlot)) {
            switch (rawSlot) {
                case 51: {
                    int size = this.display.functionalAreas.size();
                    if (size > 0) {
                        int na = this.display.getCurrentFunctionalArea() + 1;
                        this.display.setCurrentFunctionalArea(na < size ? na : 0);
                    }
                    break;
                }
                case 52:
                    this.display.switchPage(this.display.getPage() - 1);
                    break;
                case 53:
                    this.display.switchPage(this.display.getPage() + 1);
                    break;
            }
            return;
        }

        if (this.display.isOutputSlot(rawSlot)) {
            this.onOutputSlotClick(rawSlot, event, player);
            this.display.syncToBukkitInventory();
            return;
        }

        if (this.display.isInputSlot(rawSlot) || this.display.isFuelRowSlot(rawSlot)) {
            this.onMaterialSlotClick(rawSlot, event, player);
            this.display.syncToBukkitInventory();
        }
    }

    private void handleFunctionalSlotDefault(int rawSlot, int targetSlot, InventoryClickEvent event, Player player) {
        int ca = this.display.getCurrentFunctionalArea();
        if (ca >= this.display.functionalAreas.size()) return;
        FureamFunctionalArea area = this.display.functionalAreas.get(ca);
        if (!area.isStorable(this.display, rawSlot - 45) && !area.isTakable(this.display, rawSlot - 45)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = area.getGuiStack(this.display, rawSlot - 45);
        ClickType clickType = event.getClick();

        // 1. Shift click
        if (event.isShiftClick()) {
            if (!current.getType().isAir() && area.isTakable(this.display, rawSlot - 45)) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
                if (leftover.isEmpty()) {
                    area.setGuiStack(this.display, rawSlot - 45, new ItemStack(Material.AIR));
                } else {
                    current.setAmount(leftover.values().iterator().next().getAmount());
                    area.setGuiStack(this.display, rawSlot - 45, current);
                }
            }
            this.markDirtyAndSave();
            return;
        }

        // 2. Number key (Hotbar 1-9)
        if (clickType == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (hotbarItem == null || hotbarItem.getType().isAir()) {
                    if (!current.getType().isAir() && area.isTakable(this.display, rawSlot - 45)) {
                        player.getInventory().setItem(hotbarSlot, current.clone());
                        area.setGuiStack(this.display, rawSlot - 45, new ItemStack(Material.AIR));
                    }
                } else if (area.isStorable(this.display, rawSlot - 45)) {
                    if (current.getType().isAir()) {
                        area.setGuiStack(this.display, rawSlot - 45, hotbarItem.clone());
                        player.getInventory().setItem(hotbarSlot, new ItemStack(Material.AIR));
                    } else if (area.isTakable(this.display, rawSlot - 45)) {
                        area.setGuiStack(this.display, rawSlot - 45, hotbarItem.clone());
                        player.getInventory().setItem(hotbarSlot, current.clone());
                    }
                }
            }
            this.markDirtyAndSave();
            return;
        }

        // 3. Cursor empty -> Take item from slot
        if (cursor == null || cursor.getType().isAir()) {
            if (!current.getType().isAir() && area.isTakable(this.display, rawSlot - 45)) {
                if (event.isRightClick()) {
                    int half = (current.getAmount() + 1) / 2;
                    ItemStack take = current.clone();
                    take.setAmount(half);
                    current.setAmount(current.getAmount() - half);
                    event.getView().setCursor(take);
                    area.setGuiStack(this.display, rawSlot - 45, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
                } else {
                    event.getView().setCursor(current.clone());
                    area.setGuiStack(this.display, rawSlot - 45, new ItemStack(Material.AIR));
                }
            }
        } else {
            // 4. Cursor has item -> Place/Swap into slot
            if (area.isStorable(this.display, rawSlot - 45)) {
                if (current.getType().isAir()) {
                    if (event.isRightClick()) {
                        ItemStack putOne = cursor.clone();
                        putOne.setAmount(1);
                        cursor.setAmount(cursor.getAmount() - 1);
                        area.setGuiStack(this.display, rawSlot - 45, putOne);
                        event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                    } else {
                        area.setGuiStack(this.display, rawSlot - 45, cursor.clone());
                        event.getView().setCursor(new ItemStack(Material.AIR));
                    }
                } else if (current.isSimilar(cursor)) {
                    int max = current.getMaxStackSize();
                    if (event.isRightClick()) {
                        if (current.getAmount() < max) {
                            current.setAmount(current.getAmount() + 1);
                            cursor.setAmount(cursor.getAmount() - 1);
                            area.setGuiStack(this.display, rawSlot - 45, current);
                            event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                        }
                    } else {
                        int space = max - current.getAmount();
                        int move = Math.min(space, cursor.getAmount());
                        if (move > 0) {
                            current.setAmount(current.getAmount() + move);
                            cursor.setAmount(cursor.getAmount() - move);
                            area.setGuiStack(this.display, rawSlot - 45, current);
                            event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                        }
                    }
                } else if (area.isTakable(this.display, rawSlot - 45)) {
                    area.setGuiStack(this.display, rawSlot - 45, cursor.clone());
                    event.getView().setCursor(current.clone());
                }
            }
        }
        this.markDirtyAndSave();
    }

    private void markDirtyAndSave() {
        FurnacePos pos = new FurnacePos(this.display.location);
        FurnaceContext ctx = FurnaceManager.CONTEXTS.get(pos);
        if (ctx != null) {
            ctx.dirty = true;
            FurnaceManager.saveToNbt(ctx);
        }
    }

    private void onOutputSlotClick(int slotIndex, InventoryClickEvent event, Player player) {
        ItemStack current = this.display.getStack(slotIndex);
        if (current.getType().isAir()) return;

        if (event.isShiftClick()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
            if (leftover.isEmpty()) {
                this.display.setStack(slotIndex, new ItemStack(Material.AIR));
            } else {
                current.setAmount(leftover.values().iterator().next().getAmount());
                this.display.setStack(slotIndex, current);
            }
            this.markDirtyAndSave();
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            event.getView().setCursor(current.clone());
            this.display.setStack(slotIndex, new ItemStack(Material.AIR));
        } else if (cursor.isSimilar(current)) {
            int max = cursor.getMaxStackSize();
            int space = max - cursor.getAmount();
            int move = Math.min(space, current.getAmount());
            if (move > 0) {
                cursor.setAmount(cursor.getAmount() + move);
                current.setAmount(current.getAmount() - move);
                event.getView().setCursor(cursor);
                this.display.setStack(slotIndex, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
            }
        }
        this.markDirtyAndSave();
    }

    private void onMaterialSlotClick(int slotIndex, InventoryClickEvent event, Player player) {
        boolean isFuel = this.display.isFuelRowSlot(slotIndex);
        ItemStack cursor = event.getCursor();
        ItemStack current = this.display.getStack(slotIndex);

        if (event.isShiftClick()) {
            if (!current.getType().isAir()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
                if (leftover.isEmpty()) {
                    this.display.setStack(slotIndex, new ItemStack(Material.AIR));
                } else {
                    current.setAmount(leftover.values().iterator().next().getAmount());
                    this.display.setStack(slotIndex, current);
                }
            }
            this.markDirtyAndSave();
            return;
        }

        if (cursor == null || cursor.getType().isAir()) {
            if (!current.getType().isAir()) {
                if (event.isRightClick()) {
                    int half = (current.getAmount() + 1) / 2;
                    ItemStack take = current.clone();
                    take.setAmount(half);
                    current.setAmount(current.getAmount() - half);
                    event.getView().setCursor(take);
                    this.display.setStack(slotIndex, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
                } else {
                    event.getView().setCursor(current.clone());
                    this.display.setStack(slotIndex, new ItemStack(Material.AIR));
                }
            }
        } else {
            if (isFuel && !FureamFurnaceLogic.isFuel(cursor)) {
                return;
            }
            if (current.getType().isAir()) {
                if (event.isRightClick()) {
                    ItemStack putOne = cursor.clone();
                    putOne.setAmount(1);
                    cursor.setAmount(cursor.getAmount() - 1);
                    this.display.setStack(slotIndex, putOne);
                    event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                } else {
                    this.display.setStack(slotIndex, cursor.clone());
                    event.getView().setCursor(new ItemStack(Material.AIR));
                }
            } else if (current.isSimilar(cursor)) {
                int max = current.getMaxStackSize();
                if (event.isRightClick()) {
                    if (current.getAmount() < max) {
                        current.setAmount(current.getAmount() + 1);
                        cursor.setAmount(cursor.getAmount() - 1);
                        this.display.setStack(slotIndex, current);
                        event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                    }
                } else {
                    int space = max - current.getAmount();
                    int move = Math.min(space, cursor.getAmount());
                    if (move > 0) {
                        current.setAmount(current.getAmount() + move);
                        cursor.setAmount(cursor.getAmount() - move);
                        this.display.setStack(slotIndex, current);
                        event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                    }
                }
            } else {
                this.display.setStack(slotIndex, cursor.clone());
                event.getView().setCursor(current.clone());
            }
        }
        this.markDirtyAndSave();
    }

    private @NotNull ItemStack quickMoveToContainer(@NotNull ItemStack original) {
        ItemStack remaining = original.clone();
        if (FureamFurnaceLogic.isAcceptableInput(this.display.location.getWorld(), remaining, this.display.furnaceType)) {
            int stop = this.display.getInputSlotStop();
            for (int i = 0; i < stop; i++) {
                remaining = this.insertIntoSlot(i, remaining);
                if (remaining.getType().isAir() || remaining.getAmount() <= 0) {
                    this.markDirtyAndSave();
                    return new ItemStack(Material.AIR);
                }
            }
        }
        if (FureamFurnaceLogic.isFuel(remaining)) {
            int fuelStart = 18;
            int stop = this.display.getFuelSlotStop();
            for (int i = fuelStart; i < stop; i++) {
                remaining = this.insertIntoSlot(i, remaining);
                if (remaining.getType().isAir() || remaining.getAmount() <= 0) {
                    this.markDirtyAndSave();
                    return new ItemStack(Material.AIR);
                }
            }
        }
        this.markDirtyAndSave();
        return remaining;
    }

    private @NotNull ItemStack insertIntoSlot(int slotIndex, @NotNull ItemStack stack) {
        ItemStack current = this.display.getStack(slotIndex);
        if (current.getType().isAir()) {
            this.display.setStack(slotIndex, stack.clone());
            return new ItemStack(Material.AIR);
        }
        if (current.isSimilar(stack)) {
            int max = current.getMaxStackSize();
            int space = max - current.getAmount();
            int move = Math.min(space, stack.getAmount());
            if (move > 0) {
                current.setAmount(current.getAmount() + move);
                stack.setAmount(stack.getAmount() - move);
                this.display.setStack(slotIndex, current);
            }
        }
        return stack.getAmount() > 0 ? stack : new ItemStack(Material.AIR);
    }

    public void handleDrag(@NotNull InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
