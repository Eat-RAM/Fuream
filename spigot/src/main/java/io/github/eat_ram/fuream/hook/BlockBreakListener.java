package io.github.eat_ram.fuream.hook;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
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
    private static final Set<FurnacePos> BREAKING = Collections.newSetFromMap(
        new ConcurrentHashMap<FurnacePos, Boolean>()
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.handleFurnaceBreak(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        this.handleFurnaceBreak(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            this.handleFurnaceBreak(b);
        }
    }

    static void handleFurnaceBreak(Block block) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return;

        FurnacePos pos = new FurnacePos(block);
        if (!BREAKING.add(pos)) return;
        Bukkit.getScheduler().runTask(FureamMain.getInstance(), () -> BREAKING.remove(pos));
        FurnaceContext ctx = FurnaceManager.CONTEXTS.remove(pos);
        if (ctx == null) {
            ctx = FurnaceManager.getOrCreateContext(block);
            FurnaceManager.CONTEXTS.remove(pos);
        }
        if (ctx != null) {
            Location loc = block.getLocation();
            FureamFurnaceLogic.dropOnBreak(loc, ctx.data);

            FurnaceManager.closeSessions(ctx);
        }
    }
}
