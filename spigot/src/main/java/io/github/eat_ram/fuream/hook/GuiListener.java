package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class GuiListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        for (FurnaceContext ctx : FurnaceManager.CONTEXTS.values()) {
            if (ctx.activeGui != null && ctx.activeGui.bukkitInventory.equals(inv)) {
                if (ctx.handler != null && event.getWhoClicked() instanceof Player) {
                    ctx.handler.handleClick(event, (Player) event.getWhoClicked());
                    ctx.dirty = true;
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        for (FurnaceContext ctx : FurnaceManager.CONTEXTS.values()) {
            if (ctx.activeGui != null && ctx.activeGui.bukkitInventory.equals(inv)) {
                if (ctx.handler != null) {
                    ctx.handler.handleDrag(event);
                    ctx.dirty = true;
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        for (FurnaceContext ctx : FurnaceManager.CONTEXTS.values()) {
            if (ctx.activeGui != null && ctx.activeGui.bukkitInventory.equals(inv)) {
                FurnaceManager.saveToNbt(ctx);
                return;
            }
        }
    }
}
