package io.github.eat_ram.fuream.screen;

import java.util.Map;

import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.FurnaceEventCompat;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class FureamScreenHandler {
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
        if ("UNKNOWN".equals(action.name()) || "CLONE_STACK".equals(action.name()) ||
            "CREATIVE".equals(clickType.name())) {
            event.setCancelled(rawSlot < FureamScreenInventory.SIZE);
            return;
        }

        if (clickType == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            boolean changed = this.collectMatchingToCursor(event, player);
            this.display.syncToBukkitInventory();
            if (changed) this.markDirty();
            return;
        }

        // Click in player inventory with shift-click into custom GUI
        if (rawSlot >= FureamScreenInventory.SIZE) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack current = event.getCurrentItem();
                if (!ItemCompat.isEmpty(current)) {
                    ItemStack remaining = this.quickMoveToContainer(current);
                    event.setCurrentItem(ItemCompat.isEmpty(remaining) ? null : remaining);
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
                    this.display, rawSlot - FureamScreenInventory.FUNCTIONAL_START,
                    event.getHotbarButton(), clickType, player
                );
                this.display.syncToBukkitInventory();
            }
            return;
        }

        if (this.display.isBarOrDecorSlot(rawSlot)) {
            switch (rawSlot) {
                case FureamScreenInventory.NEXT_FUNCTIONAL_SLOT: {
                    int size = this.display.functionalAreas.size();
                    if (size > 0) {
                        int na = this.display.getCurrentFunctionalArea() + 1;
                        this.display.setCurrentFunctionalArea(na < size ? na : 0);
                    }
                    break;
                }
                case FureamScreenInventory.PREVIOUS_PAGE_SLOT:
                    this.display.switchPage(this.display.getPage() - 1);
                    break;
                case FureamScreenInventory.NEXT_PAGE_SLOT:
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
        int functionalSlot = rawSlot - FureamScreenInventory.FUNCTIONAL_START;
        if (!area.isStorable(this.display, functionalSlot) && !area.isTakable(this.display, functionalSlot)) return;

        ItemStack cursor = event.getCursor();
        ItemStack current = area.getGuiStack(this.display, functionalSlot);
        ClickType clickType = event.getClick();

        // 1. Shift click
        if (event.isShiftClick()) {
            boolean changed = false;
            if (!ItemCompat.isEmpty(current) && area.isTakable(this.display, functionalSlot)) {
                int before = current.getAmount();
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
                if (leftover.isEmpty()) {
                    area.setGuiStack(this.display, functionalSlot, new ItemStack(Material.AIR));
                    changed = true;
                } else {
                    current.setAmount(leftover.values().iterator().next().getAmount());
                    area.setGuiStack(this.display, functionalSlot, current);
                    changed = current.getAmount() != before;
                }
            }
            if (changed) this.markDirty();
            return;
        }

        // 2. Number key (Hotbar 1-9)
        if (clickType == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (ItemCompat.isEmpty(hotbarItem)) {
                    if (!ItemCompat.isEmpty(current) && area.isTakable(this.display, functionalSlot)) {
                        player.getInventory().setItem(hotbarSlot, current.clone());
                        area.setGuiStack(this.display, functionalSlot, new ItemStack(Material.AIR));
                        this.markDirty();
                    }
                } else if (area.isStorable(this.display, functionalSlot)) {
                    if (ItemCompat.isEmpty(current)) {
                        area.setGuiStack(this.display, functionalSlot, hotbarItem.clone());
                        player.getInventory().setItem(hotbarSlot, new ItemStack(Material.AIR));
                        this.markDirty();
                    } else if (area.isTakable(this.display, functionalSlot)) {
                        area.setGuiStack(this.display, functionalSlot, hotbarItem.clone());
                        player.getInventory().setItem(hotbarSlot, current.clone());
                        this.markDirty();
                    }
                }
            }
            return;
        }

        // 3. Cursor empty -> Take item from slot
        if (ItemCompat.isEmpty(cursor)) {
            if (!ItemCompat.isEmpty(current) && area.isTakable(this.display, functionalSlot)) {
                if (event.isRightClick()) {
                    int half = (current.getAmount() + 1) / 2;
                    ItemStack take = current.clone();
                    take.setAmount(half);
                    current.setAmount(current.getAmount() - half);
                    event.getView().setCursor(take);
                    area.setGuiStack(this.display, functionalSlot, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
                } else {
                    event.getView().setCursor(current.clone());
                    area.setGuiStack(this.display, functionalSlot, new ItemStack(Material.AIR));
                }
            }
        } else {
            // 4. Cursor has item -> Place/Swap into slot
            if (area.isStorable(this.display, functionalSlot)) {
                if (ItemCompat.isEmpty(current)) {
                    if (event.isRightClick()) {
                        ItemStack putOne = cursor.clone();
                        putOne.setAmount(1);
                        cursor.setAmount(cursor.getAmount() - 1);
                        area.setGuiStack(this.display, functionalSlot, putOne);
                        event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                    } else {
                        area.setGuiStack(this.display, functionalSlot, cursor.clone());
                        event.getView().setCursor(new ItemStack(Material.AIR));
                    }
                } else if (current.isSimilar(cursor)) {
                    int max = current.getMaxStackSize();
                    if (event.isRightClick()) {
                        if (current.getAmount() < max) {
                            current.setAmount(current.getAmount() + 1);
                            cursor.setAmount(cursor.getAmount() - 1);
                            area.setGuiStack(this.display, functionalSlot, current);
                            event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                        }
                    } else {
                        int space = max - current.getAmount();
                        int move = Math.min(space, cursor.getAmount());
                        if (move > 0) {
                            current.setAmount(current.getAmount() + move);
                            cursor.setAmount(cursor.getAmount() - move);
                            area.setGuiStack(this.display, functionalSlot, current);
                            event.getView().setCursor(cursor.getAmount() > 0 ? cursor : new ItemStack(Material.AIR));
                        }
                    }
                } else if (area.isTakable(this.display, functionalSlot)) {
                    area.setGuiStack(this.display, functionalSlot, cursor.clone());
                    event.getView().setCursor(current.clone());
                }
            }
        }
        this.markDirty();
    }

    private void markDirty() {
        FurnacePos pos = new FurnacePos(this.display.location);
        FurnaceContext ctx = FurnaceManager.getContext(pos);
        if (ctx != null) {
            ctx.dirty = true;
        }
    }

    private void onOutputSlotClick(int slotIndex, InventoryClickEvent event, Player player) {
        ItemStack current = this.display.getStack(slotIndex);
        if (ItemCompat.isEmpty(current)) return;
        ItemStack extractIdentity = current.clone();
        extractIdentity.setAmount(1);
        int moved = 0;

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9 &&
                ItemCompat.isEmpty(player.getInventory().getItem(hotbarSlot))) {
                player.getInventory().setItem(hotbarSlot, current.clone());
                moved = current.getAmount();
                this.display.setStack(slotIndex, new ItemStack(Material.AIR));
            }
            this.notifyExtract(player, extractIdentity, moved);
            if (moved > 0) this.markDirty();
            return;
        }

        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            moved = event.getClick() == ClickType.DROP ? 1 : current.getAmount();
            ItemStack dropped = current.clone();
            dropped.setAmount(moved);
            if (!this.dropThroughEvent(player, dropped)) return;
            current.setAmount(current.getAmount() - moved);
            this.display.setStack(slotIndex, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
            this.notifyExtract(player, dropped, moved);
            this.markDirty();
            return;
        }

        if (event.isShiftClick()) {
            int before = current.getAmount();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
            if (leftover.isEmpty()) {
                this.display.setStack(slotIndex, new ItemStack(Material.AIR));
            } else {
                current.setAmount(leftover.values().iterator().next().getAmount());
                this.display.setStack(slotIndex, current);
            }
            moved = before - (leftover.isEmpty() ? 0 : current.getAmount());
            this.notifyExtract(player, extractIdentity, moved);
            if (moved > 0) this.markDirty();
            return;
        }

        ItemStack cursor = event.getCursor();
        if (ItemCompat.isEmpty(cursor)) {
            moved = event.isRightClick() ? (current.getAmount() + 1) / 2 : current.getAmount();
            ItemStack taken = current.clone();
            taken.setAmount(moved);
            event.getView().setCursor(taken);
            current.setAmount(current.getAmount() - moved);
            this.display.setStack(slotIndex, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
        } else if (cursor.isSimilar(current)) {
            int max = cursor.getMaxStackSize();
            int space = max - cursor.getAmount();
            int move = Math.min(space, current.getAmount());
            if (move > 0) {
                moved = move;
                cursor.setAmount(cursor.getAmount() + move);
                current.setAmount(current.getAmount() - move);
                event.getView().setCursor(cursor);
                this.display.setStack(slotIndex, current.getAmount() > 0 ? current : new ItemStack(Material.AIR));
            }
        }
        this.notifyExtract(player, extractIdentity, moved);
        if (moved > 0) this.markDirty();
    }

    private boolean collectMatchingToCursor(InventoryClickEvent event, Player player) {
        ItemStack cursor = event.getCursor();
        if (ItemCompat.isEmpty(cursor)) return false;
        int space = cursor.getMaxStackSize() - cursor.getAmount();
        int extracted = 0;
        for (int i = 0; i < this.display.data.outputs.size() && space > 0; i++) {
            ItemStack stack = this.display.data.outputs.get(i);
            if (ItemCompat.isEmpty(stack) || !stack.isSimilar(cursor)) continue;
            int move = Math.min(space, stack.getAmount());
            cursor.setAmount(cursor.getAmount() + move);
            stack.setAmount(stack.getAmount() - move);
            if (stack.getAmount() <= 0) this.display.data.outputs.set(i, ItemCompat.empty());
            space -= move;
            extracted += move;
        }
        for (int i = 0; i < player.getInventory().getSize() && space > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemCompat.isEmpty(stack) || !stack.isSimilar(cursor)) continue;
            int move = Math.min(space, stack.getAmount());
            cursor.setAmount(cursor.getAmount() + move);
            stack.setAmount(stack.getAmount() - move);
            player.getInventory().setItem(i, stack.getAmount() > 0 ? stack : null);
            space -= move;
        }
        event.getView().setCursor(cursor);
        this.notifyExtract(player, cursor, extracted);
        return extracted > 0;
    }

    private void notifyExtract(Player player, ItemStack stack, int amount) {
        if (amount > 0 && !ItemCompat.isEmpty(stack)) {
            FurnaceEventCompat.fireExtract(
                player, this.display.location.getBlock(), stack.getType(), amount
            );
        }
    }

    private boolean dropThroughEvent(Player player, ItemStack stack) {
        Item entity = player.getWorld().dropItem(player.getEyeLocation(), stack.clone());
        PlayerDropItemEvent event = new PlayerDropItemEvent(player, entity);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            entity.remove();
            return false;
        }
        return true;
    }

    private void onMaterialSlotClick(int slotIndex, InventoryClickEvent event, Player player) {
        boolean isFuel = this.display.isFuelRowSlot(slotIndex);
        ItemStack cursor = event.getCursor();
        ItemStack current = this.display.getStack(slotIndex);

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot < 0 || hotbarSlot >= 9) return;
            ItemStack hotbar = player.getInventory().getItem(hotbarSlot);
            if (!ItemCompat.isEmpty(hotbar) && !this.display.isValid(slotIndex, hotbar)) return;
            player.getInventory().setItem(
                hotbarSlot, ItemCompat.isEmpty(current) ? null : current.clone()
            );
            this.display.setStack(
                slotIndex, ItemCompat.isEmpty(hotbar) ? ItemCompat.empty() : hotbar.clone()
            );
            this.markDirty();
            return;
        }

        if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
            if (ItemCompat.isEmpty(current)) return;
            int amount = event.getClick() == ClickType.DROP ? 1 : current.getAmount();
            ItemStack dropped = current.clone();
            dropped.setAmount(amount);
            if (!this.dropThroughEvent(player, dropped)) return;
            current.setAmount(current.getAmount() - amount);
            this.display.setStack(
                slotIndex, current.getAmount() <= 0 ? ItemCompat.empty() : current
            );
            this.markDirty();
            return;
        }

        if (event.isShiftClick()) {
            int before = ItemCompat.isEmpty(current) ? 0 : current.getAmount();
            if (!ItemCompat.isEmpty(current)) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(current.clone());
                if (leftover.isEmpty()) {
                    this.display.setStack(slotIndex, new ItemStack(Material.AIR));
                } else {
                    current.setAmount(leftover.values().iterator().next().getAmount());
                    this.display.setStack(slotIndex, current);
                }
            }
            ItemStack afterStack = this.display.getStack(slotIndex);
            int after = ItemCompat.isEmpty(afterStack) ? 0 : afterStack.getAmount();
            if (before != after) this.markDirty();
            return;
        }

        if (ItemCompat.isEmpty(cursor)) {
            if (!ItemCompat.isEmpty(current)) {
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
            Material bucket = Material.matchMaterial("BUCKET");
            if (isFuel && !FureamFurnaceLogic.isFuel(cursor) &&
                (bucket == null || cursor.getType() != bucket)) {
                return;
            }
            if (ItemCompat.isEmpty(current)) {
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
        this.markDirty();
    }

    private @NotNull ItemStack quickMoveToContainer(@NotNull ItemStack original) {
        ItemStack remaining = original.clone();
        int originalAmount = original.getAmount();
        if (FureamFurnaceLogic.isAcceptableInput(this.display.location.getWorld(), remaining, this.display.furnaceType)) {
            int stop = this.display.getInputSlotStop();
            for (int i = 0; i < stop; i++) {
                remaining = this.insertIntoSlot(i, remaining);
                if (ItemCompat.isEmpty(remaining)) {
                    this.markDirty();
                    return new ItemStack(Material.AIR);
                }
            }
        }
        Material bucket = Material.matchMaterial("BUCKET");
        if (FureamFurnaceLogic.isFuel(remaining) ||
            bucket != null && remaining.getType() == bucket) {
            int fuelStart = 18;
            int stop = this.display.getFuelSlotStop();
            for (int i = fuelStart; i < stop; i++) {
                remaining = this.insertIntoSlot(i, remaining);
                if (ItemCompat.isEmpty(remaining)) {
                    this.markDirty();
                    return new ItemStack(Material.AIR);
                }
            }
        }
        if (remaining.getAmount() != originalAmount) this.markDirty();
        return remaining;
    }

    private @NotNull ItemStack insertIntoSlot(int slotIndex, @NotNull ItemStack stack) {
        ItemStack current = this.display.getStack(slotIndex);
        if (ItemCompat.isEmpty(current)) {
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
        boolean touchesDisplay = false;
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot >= FureamScreenInventory.SIZE) continue;
            touchesDisplay = true;
            if (!this.display.isValid(rawSlot, entry.getValue())) {
                event.setCancelled(true);
                return;
            }
        }
        if (!touchesDisplay) return;
        event.setCancelled(true);
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot < FureamScreenInventory.SIZE) {
                this.display.setStack(rawSlot, entry.getValue().clone());
            } else {
                event.getView().setItem(rawSlot, entry.getValue().clone());
            }
        }
        event.getView().setCursor(ItemCompat.isEmpty(event.getCursor()) ? null : event.getCursor().clone());
        this.markDirty();
        this.display.refreshVisuals();
    }
}
