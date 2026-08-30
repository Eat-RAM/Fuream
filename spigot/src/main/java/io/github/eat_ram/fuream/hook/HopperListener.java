package io.github.eat_ram.fuream.hook;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.logic.FuelTable;
import io.github.eat_ram.fuream.compat.ItemCompat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class HopperListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        Inventory src = event.getSource();

        // Hopper pushing INTO Furnace
        Location furnaceLoc = inventoryLocation(dest);
        if (dest instanceof FurnaceInventory && furnaceLoc != null) {
            Block block = furnaceLoc.getBlock();
            FurnaceType type = FureamMain.getFurnaceType(block);
            if (type != null) {
                FureamWorldConfig config = FureamMain.getWorldConfig(furnaceLoc.getWorld());
                if (config != null && config.getEnabledFurnaceTypes().contains(type)) {
                    Location srcLoc = inventoryLocation(src);
                    ItemStack itemToMove = event.getItem();
                    boolean isTop = srcLoc != null && srcLoc.getBlockY() > furnaceLoc.getBlockY();

                    if (isTop) {
                        // Top Hopper -> Inputs: Filter non-smeltables if configured
                        if (config.getPreventsHopperInsertNonSmeltable().contains(type)) {
                            if (!FureamFurnaceLogic.isAcceptableInput(furnaceLoc.getWorld(), itemToMove, type)) {
                                event.setCancelled(true);
                            }
                        }
                    } else {
                        // Side Hopper -> Fuels: Filter non-fuel items
                        if (!FuelTable.isFuel(itemToMove) && itemToMove.getType() != Material.BUCKET) {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }

    private static Location inventoryLocation(Inventory inventory) {
        Object holder = inventory.getHolder();
        if (holder instanceof org.bukkit.block.BlockState) {
            return ((org.bukkit.block.BlockState) holder).getLocation();
        }
        if (holder instanceof org.bukkit.entity.Entity) {
            return ((org.bukkit.entity.Entity) holder).getLocation();
        }
        try {
            Object value = inventory.getClass().getMethod("getLocation").invoke(inventory);
            return value instanceof Location ? (Location) value : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
