package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.screen.FureamScreenHandler;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class FurnaceOpenListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return;

        Player player = event.getPlayer();
        if (player.isSneaking() && event.getItem() != null && !event.getItem().getType().isAir()) {
            return;
        }

        FureamWorldConfig config = FureamMain.getWorldConfig(block.getWorld());
        if (config == null || !config.getEnabledFurnaceTypes().contains(type)) {
            return;
        }

        event.setCancelled(true);

        FurnaceContext ctx = FurnaceManager.getOrCreateContext(block);
        if (ctx == null) return;

        if (ctx.activeGui == null) {
            ctx.activeGui = new FureamScreenInventory(block.getLocation(), type, ctx.data, config);
            ctx.handler = new FureamScreenHandler(ctx.activeGui);
        }

        ctx.activeGui.burnTime = ctx.burnTime;
        ctx.activeGui.fuelTimeTotal = Math.max(1, ctx.fuelTimeTotal);
        ctx.activeGui.cookTime = ctx.cookTime;
        ctx.activeGui.cookTimeTotal = Math.max(1, ctx.cookTimeTotal);
        ctx.activeGui.refreshVisuals();

        player.openInventory(ctx.activeGui.bukkitInventory);
    }
}
