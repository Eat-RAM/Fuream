package io.github.eat_ram.fuream.hook;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

public class BlockBreakListener implements Listener {
    private static final Set<FurnacePos> PENDING = new HashSet<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.handleFurnaceBreak(event.getBlock(), dropsItems(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        this.handleFurnaceBreak(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        boolean dropItems = event.getYield() > 0f;
        for (Block b : event.blockList()) {
            this.handleFurnaceBreak(b, dropItems);
        }
    }

    static void handleFurnaceBreak(Block block, boolean dropItems) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return;

        FurnacePos pos = new FurnacePos(block);
        if (!PENDING.add(pos)) return;
        FurnaceContext context = FurnaceManager.prepareForRemoval(block);
        FureamFurnaceData snapshot = context == null ? null : context.data.copy();
        Location location = block.getLocation().clone();
        Bukkit.getScheduler().runTask(FureamMain.getInstance(), () -> {
            try {
                FurnaceType remaining = FureamMain.getFurnaceType(location.getBlock());
                if (remaining == type) return;
                if (dropItems && snapshot != null) FureamFurnaceLogic.dropOnBreak(location, snapshot);
            } finally {
                PENDING.remove(pos);
            }
        });
    }

    private static boolean dropsItems(BlockBreakEvent event) {
        try {
            Method method = event.getClass().getMethod("isDropItems");
            return !Boolean.FALSE.equals(method.invoke(event));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }
}
