package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.List;

import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FuelTable;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/** Maintains the comparator view exposed by Fabric's three-slot masked inventory. */
public final class ComparatorCompat {
    public static int calculate(FureamFurnaceData data) {
        ItemStack input = first(data.inputs, false);
        ItemStack fuel = first(data.fuels, true);
        ItemStack output = first(data.outputs, false);
        ItemStack[] masked = {input, fuel, output};
        float fullness = 0f;
        int occupied = 0;
        for (ItemStack stack : masked) {
            if (ItemCompat.isEmpty(stack)) continue;
            fullness += Math.min(1f, (float) stack.getAmount() / Math.max(1, stack.getMaxStackSize()));
            occupied++;
        }
        return occupied == 0 ? 0 : (int) Math.floor(fullness / 3f * 14f) + 1;
    }

    public static void requestRecalculation(Block furnace) {
        // Bukkit has no version-stable comparator notification API. Re-applying
        // the current snapshot with physics is the least invasive public hook.
        furnace.getState().update(false, true);
    }

    public static Block sourceBlock(Block comparator) {
        try {
            BlockFace facing = BlockFace.valueOf(facingName(comparator));
            return comparator.getRelative(facing.getOppositeFace());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ItemStack first(List<ItemStack> stacks, boolean fuelOnly) {
        for (ItemStack stack : stacks) {
            if (!ItemCompat.isEmpty(stack) && (!fuelOnly || FuelTable.isFuel(stack))) return stack;
        }
        return ItemCompat.empty();
    }

    private static String facingName(Block comparator) {
        try {
            Object data = comparator.getClass().getMethod("getBlockData").invoke(comparator);
            Method method = data.getClass().getMethod("getFacing");
            method.setAccessible(true);
            Object facing = method.invoke(data);
            return facing instanceof Enum ? ((Enum<?>) facing).name() : String.valueOf(facing);
        } catch (ReflectiveOperationException ignored) {
            try {
                int data = ((Number) comparator.getClass().getMethod("getData").invoke(comparator)).intValue() & 3;
                return new String[] {"NORTH", "EAST", "SOUTH", "WEST"}[data];
            } catch (ReflectiveOperationException e) {
                return "";
            }
        }
    }

    private ComparatorCompat() {
    }
}
