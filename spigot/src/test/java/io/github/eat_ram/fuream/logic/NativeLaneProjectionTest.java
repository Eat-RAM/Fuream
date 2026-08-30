package io.github.eat_ram.fuream.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class NativeLaneProjectionTest {
    @Test
    public void publishingKeepsCanonicalVirtualStack() {
        List<ItemStack> lane = lane(stack(Material.IRON_INGOT, 12));
        NativeLaneProjection.State state = new NativeLaneProjection.State();

        ItemStack published = NativeLaneProjection.publish(
            lane, null, NativeLaneProjection.SelectionMode.FIRST_PRESENT, state
        );

        assertEquals(12, published.getAmount());
        assertEquals(12, lane.get(0).getAmount());
        assertEquals(0, state.getSourceSlot());
    }

    @Test
    public void hopperRemovalReplacesCanonicalAmountWithoutDuplication() {
        List<ItemStack> lane = lane(stack(Material.IRON_INGOT, 12));
        NativeLaneProjection.State state = new NativeLaneProjection.State();
        ItemStack published = NativeLaneProjection.publish(
            lane, null, NativeLaneProjection.SelectionMode.FIRST_PRESENT, state
        );
        published.setAmount(7);

        assertNull(NativeLaneProjection.reconcile(lane, published, 1, state));
        assertEquals(7, lane.get(0).getAmount());
    }

    @Test
    public void completeHopperRemovalClearsOnlyProjectedSource() {
        List<ItemStack> lane = lane(stack(Material.IRON_INGOT, 12), stack(Material.GOLD_INGOT, 4));
        NativeLaneProjection.State state = new NativeLaneProjection.State();
        NativeLaneProjection.publish(lane, null, NativeLaneProjection.SelectionMode.FIRST_PRESENT, state);

        assertNull(NativeLaneProjection.reconcile(lane, null, 2, state));
        assertEquals(Material.AIR, lane.get(0).getType());
        assertEquals(4, lane.get(1).getAmount());
    }

    @Test
    public void fuelProjectionPublishesOnlyBucketRemainders() {
        List<ItemStack> lane = lane(stack(Material.COAL, 8), stack(Material.BUCKET, 1));
        ItemStack published = NativeLaneProjection.publish(
            lane, null, NativeLaneProjection.SelectionMode.BUCKET_REMAINDER,
            new NativeLaneProjection.State()
        );
        assertEquals(Material.BUCKET, published.getType());
    }

    private static List<ItemStack> lane(ItemStack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    private static ItemStack stack(Material material, int amount) {
        return new TestItemStack(material, amount);
    }

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

        @Override
        public ItemMeta getItemMeta() {
            return null;
        }

        @Override
        public ItemStack clone() {
            return new TestItemStack(this.getType(), this.getAmount());
        }
    }
}
