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
import java.lang.reflect.InvocationTargetException;

public class FurnaceOpenListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isOffHand(event) || isBlockUseDenied(event)) return;

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

        FurnaceContext ctx = FurnaceManager.getOrCreateContext(block);
        if (ctx == null) return;
        ctx = FurnaceManager.prepareForInteraction(ctx.pos);
        if (ctx == null) return;

        event.setCancelled(true);

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
        FurnaceManager.finishInteraction(ctx);
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
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException failure) {
            return false;
        }
    }

    private static boolean isOffHand(PlayerInteractEvent event) {
        try {
            Object hand = event.getClass().getMethod("getHand").invoke(event);
            return hand instanceof Enum && "OFF_HAND".equals(((Enum<?>) hand).name());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isBlockUseDenied(PlayerInteractEvent event) {
        try {
            Object result = event.getClass().getMethod("useInteractedBlock").invoke(event);
            return result instanceof Enum && "DENY".equals(((Enum<?>) result).name());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
