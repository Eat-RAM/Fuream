package io.github.eat_ram.fuream.logic;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FureamFurnaceEngineTest {
    @Test
    public void emptyVanillaResultDoesNotRemoveFirstVirtualOutput() {
        List<ItemStack> outputs = outputSlots(stack(Material.IRON_INGOT, 1));

        ItemStack remainder = FureamFurnaceEngine.recoverVanillaOutput(outputs, null, 1);

        assertNull(remainder);
        assertEquals(Material.IRON_INGOT, outputs.get(0).getType());
        assertEquals(1, outputs.get(0).getAmount());
    }

    @Test
    public void strandedVanillaResultMovesBackIntoVirtualOutputs() {
        List<ItemStack> outputs = outputSlots(stack(Material.AIR, 1));

        ItemStack remainder = FureamFurnaceEngine.recoverVanillaOutput(
            outputs, stack(Material.IRON_INGOT, 1), 1
        );

        assertNull(remainder);
        assertEquals(Material.IRON_INGOT, outputs.get(0).getType());
        assertEquals(1, outputs.get(0).getAmount());
    }

    @Test
    public void fullVirtualOutputsKeepVanillaRemainder() {
        List<ItemStack> outputs = outputSlots(stack(Material.IRON_INGOT, 64));

        ItemStack remainder = FureamFurnaceEngine.recoverVanillaOutput(
            outputs, stack(Material.IRON_INGOT, 2), 1
        );

        assertEquals(64, outputs.get(0).getAmount());
        assertNotNull(remainder);
        assertEquals(Material.IRON_INGOT, remainder.getType());
        assertEquals(2, remainder.getAmount());
    }

    @Test
    public void partiallyFullVirtualOutputsOnlyLeaveUninsertedRemainder() {
        List<ItemStack> outputs = outputSlots(stack(Material.IRON_INGOT, 63));

        ItemStack remainder = FureamFurnaceEngine.recoverVanillaOutput(
            outputs, stack(Material.IRON_INGOT, 2), 1
        );

        assertEquals(64, outputs.get(0).getAmount());
        assertNotNull(remainder);
        assertEquals(Material.IRON_INGOT, remainder.getType());
        assertEquals(1, remainder.getAmount());
    }

    private static List<ItemStack> outputSlots(ItemStack first) {
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(first);
        return outputs;
    }

    private static ItemStack stack(Material material, int amount) {
        return new TestItemStack(material, amount);
    }

    /** Avoids the Bukkit ItemFactory dependency in this pure unit test. */
    private static final class TestItemStack extends ItemStack {
        private TestItemStack(Material material, int amount) {
            super(material, amount);
        }

        @Override
        public boolean isSimilar(ItemStack stack) {
            return stack != null && this.getType() == stack.getType();
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }
}
