package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class GuiListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        FureamScreenInventory session = session(event.getInventory());
        if (session == null || !(event.getWhoClicked() instanceof Player)) return;
        FurnaceManager.FurnaceContext ctx = FurnaceManager.prepareForInteraction(
            new FurnacePos(session.location)
        );
        if (ctx == null) {
            event.setCancelled(true);
            return;
        }
        session.handler.handleClick(event, (Player) event.getWhoClicked());
        FurnaceManager.finishInteraction(ctx);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        FureamScreenInventory session = session(event.getInventory());
        if (session == null) return;
        FurnaceManager.FurnaceContext ctx = FurnaceManager.prepareForInteraction(
            new FurnacePos(session.location)
        );
        if (ctx == null) {
            event.setCancelled(true);
            return;
        }
        session.handler.handleDrag(event);
        FurnaceManager.finishInteraction(ctx);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        FureamScreenInventory session = session(event.getInventory());
        if (session == null) return;
        FurnaceManager.FurnaceContext ctx = context(session);
        if (ctx != null) {
            ctx.sessions.remove(session.viewerId, session);
            FurnaceManager.saveToNbt(ctx);
        }
    }

    private static FureamScreenInventory session(Inventory inventory) {
        return inventory.getHolder() instanceof FureamScreenInventory
            ? (FureamScreenInventory) inventory.getHolder() : null;
    }

    private static FurnaceManager.FurnaceContext context(FureamScreenInventory session) {
        return FurnaceManager.getContext(new FurnacePos(session.location));
    }
}
