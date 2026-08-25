package io.github.eat_ram.fuream.hook;

import java.lang.reflect.Field;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class BlockBreakListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        this.handleFurnaceBreak(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        this.handleFurnaceBreak(event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block b : event.blockList()) {
            this.handleFurnaceBreak(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block b : event.blockList()) {
            this.handleFurnaceBreak(b);
        }
    }

    private void handleFurnaceBreak(Block block) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return;

        FurnacePos pos = new FurnacePos(block);
        FurnaceContext ctx = FurnaceManager.CONTEXTS.remove(pos);
        if (ctx == null) {
            ctx = FurnaceManager.getOrCreateContext(block);
            FurnaceManager.CONTEXTS.remove(pos);
        }
        if (ctx != null) {
            Location loc = block.getLocation();
            FureamFurnaceLogic.dropOnBreak(loc, ctx.data);

            // Handle extra data items (e.g. upgrades)
            for (FureamData extra : ctx.extraData.values()) {
                try {
                    Field rateField = extra.getClass().getField("rateUpgradeStack");
                    ItemStack rate = (ItemStack) rateField.get(extra);
                    if (rate != null && !rate.getType().isAir()) {
                        loc.getWorld().dropItemNaturally(loc, rate.clone());
                    }
                    Field fuelField = extra.getClass().getField("fuelUpgradeStack");
                    ItemStack fuel = (ItemStack) fuelField.get(extra);
                    if (fuel != null && !fuel.getType().isAir()) {
                        loc.getWorld().dropItemNaturally(loc, fuel.clone());
                    }
                } catch (Exception ignored) {}
            }

            if (ctx.activeGui != null) {
                for (org.bukkit.entity.HumanEntity viewer : new java.util.ArrayList<>(ctx.activeGui.bukkitInventory.getViewers())) {
                    viewer.closeInventory();
                }
            }
        }
    }
}
