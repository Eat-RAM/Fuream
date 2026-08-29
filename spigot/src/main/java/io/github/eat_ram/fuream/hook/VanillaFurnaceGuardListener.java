package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.compat.FurnaceEventDispatcher;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;

/** Prevents the vanilla engine from consuming Fuream's native ingress/projection slots. */
public final class VanillaFurnaceGuardListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(FurnaceBurnEvent event) {
        if (!FurnaceEventDispatcher.isDispatching() && FurnaceManager.isManagedActive(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmelt(FurnaceSmeltEvent event) {
        if (!FurnaceEventDispatcher.isDispatching() && FurnaceManager.isManagedActive(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
