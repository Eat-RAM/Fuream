package io.github.eat_ram.fuream.hook;

import java.util.Iterator;
import java.util.Map;

import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BlockState tile : event.getChunk().getTileEntities()) {
            if (tile instanceof Furnace) {
                FurnaceManager.getOrCreateContext(tile.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        String worldName = chunk.getWorld().getName();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        for (Iterator<Map.Entry<FurnacePos, FurnaceContext>> it = FurnaceManager.CONTEXTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<FurnacePos, FurnaceContext> entry = it.next();
            FurnacePos pos = entry.getKey();
            if (pos.worldName.equals(worldName) && (pos.x >> 4) == cx && (pos.z >> 4) == cz) {
                FurnaceContext ctx = entry.getValue();
                FurnaceManager.closeSessions(ctx);
                if (ctx.dirty || ctx.data.hasAny()) {
                    FurnaceManager.saveToNbt(ctx);
                }
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (block.getState() instanceof Furnace) {
            FurnaceManager.getOrCreateContext(block);
        }
    }
}
