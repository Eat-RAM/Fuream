package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.compat.RecipeCompat;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

/** Rebuilds the immutable recipe snapshot after plugins can change registrations. */
public final class RecipeIndexListener implements Listener {
    private boolean scheduled;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        schedule();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        schedule();
    }

    private void schedule() {
        if (this.scheduled) return;
        this.scheduled = true;
        Bukkit.getScheduler().runTask(FureamMain.getInstance(), () -> {
            this.scheduled = false;
            RecipeCompat.rebuild();
        });
    }
}
