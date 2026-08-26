package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.compat.ItemCompat;
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
        if (player.isSneaking() && !ItemCompat.isEmpty(player.getItemInHand())) {
            return;
        }

        FureamWorldConfig config = FureamMain.getWorldConfig(block.getWorld());
        if (config == null || !config.getEnabledFurnaceTypes().contains(type)) {
            return;
        }
        if (!canOpenLocked(block, player)) return;

        event.setCancelled(true);

        FurnaceContext ctx = FurnaceManager.getOrCreateContext(block);
        if (ctx == null) return;

        FureamScreenInventory session = new FureamScreenInventory(
            player.getUniqueId(), block.getLocation(), type, ctx.data, config
        );
        ctx.sessions.put(player.getUniqueId(), session);
        session.burnTime = ctx.burnTime;
        session.fuelTimeTotal = Math.max(1, ctx.fuelTimeTotal);
        session.cookTime = ctx.cookTime;
        session.cookTimeTotal = Math.max(1, ctx.cookTimeTotal);
        session.refreshVisuals();
        player.openInventory(session.bukkitInventory);
    }

    private static boolean canOpenLocked(Block block, Player player) {
        try {
            Object state = block.getState();
            Object locked = state.getClass().getMethod("isLocked").invoke(state);
            if (!Boolean.TRUE.equals(locked)) return true;
            Object lock = state.getClass().getMethod("getLock").invoke(state);
            if (!(lock instanceof String)) return false;
            org.bukkit.inventory.ItemStack held = player.getItemInHand();
            return !ItemCompat.isEmpty(held) && held.hasItemMeta() &&
                held.getItemMeta().hasDisplayName() && lock.equals(held.getItemMeta().getDisplayName());
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }
}
