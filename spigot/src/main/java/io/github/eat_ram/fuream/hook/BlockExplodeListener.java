package io.github.eat_ram.fuream.hook;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;

/** Kept out of the base listener because BlockExplodeEvent is absent on 1.7. */
public final class BlockExplodeListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) BlockBreakListener.handleFurnaceBreak(block);
    }
}
