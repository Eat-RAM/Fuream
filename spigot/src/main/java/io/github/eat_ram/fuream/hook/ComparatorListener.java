package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.compat.ComparatorCompat;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

/** Supplies comparator recalculations with the Fabric-compatible masked signal. */
public final class ComparatorListener implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if (!event.getBlock().getType().name().contains("COMPARATOR")) return;
        Block source = ComparatorCompat.sourceBlock(event.getBlock());
        if (source == null) return;
        FurnaceType type = FureamMain.getFurnaceType(source);
        if (type == null) return;
        FureamWorldConfig config = FureamMain.getWorldConfig(source.getWorld());
        if (config == null || !config.getEnabledFurnaceTypes().contains(type)) return;
        FurnaceManager.FurnaceContext context = FurnaceManager.CONTEXTS.get(new FurnacePos(source));
        if (context == null) context = FurnaceManager.getOrCreateContext(source);
        if (context != null) event.setNewCurrent(ComparatorCompat.calculate(context.data));
    }
}
