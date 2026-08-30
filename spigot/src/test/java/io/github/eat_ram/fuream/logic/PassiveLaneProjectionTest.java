package io.github.eat_ram.fuream.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.eat_ram.fuream.logic.PassiveLaneProjection.SelectionMode;
import io.github.eat_ram.fuream.logic.PassiveLaneProjection.State;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PassiveLaneProjectionTest {
    @Test
    public void inputAndOutputProjectOnlyFirstPresentStack() {
        List<ItemStack> lane = lane(
            air(), stack(Material.DIAMOND, 32), stack(Material.DIAMOND, 16)
        );
        State state = new State();

        ItemStack projected = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        );

        assertEquals(Material.DIAMOND, projected.getType());
        assertEquals(32, projected.getAmount());
        assertEquals(1, state.getSourceSlot());
        assertEquals(Material.AIR, lane.get(1).getType());
        assertEquals(Material.DIAMOND, lane.get(2).getType());
        assertEquals(16, lane.get(2).getAmount());
    }

    @Test
    public void removingProjectedStackDoesNotTouchHiddenStack() {
        List<ItemStack> lane = lane(
            stack(Material.DIAMOND, 32), stack(Material.DIAMOND, 16)
        );
        State state = new State();

        PassiveLaneProjection.begin(lane, null, SelectionMode.FIRST_PRESENT, state);
        ItemStack nativeAfterPlayerRemoval = null;

        assertNull(nativeAfterPlayerRemoval);
        assertEquals(Material.DIAMOND, lane.get(1).getType());
        assertEquals(16, lane.get(1).getAmount());
        assertEquals(0, state.getSourceSlot());
    }

    @Test
    public void fuelPrefersBurnableStackOverEarlierNonFuel() {
        List<ItemStack> lane = lane(
            stack(Material.BUCKET, 1), stack(Material.COAL, 8), stack(Material.STICK, 3)
        );

        ItemStack projected = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FUEL_THEN_PRESENT, new State()
        );

        assertEquals(Material.COAL, projected.getType());
        assertEquals(Material.BUCKET, lane.get(0).getType());
        assertEquals(Material.AIR, lane.get(1).getType());
    }

    @Test
    public void fuelFallsBackToFirstPresentStack() {
        List<ItemStack> lane = lane(air(), stack(Material.BUCKET, 1), stack(Material.IRON_INGOT, 2));
        State state = new State();

        ItemStack projected = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FUEL_THEN_PRESENT, state
        );

        assertEquals(Material.BUCKET, projected.getType());
        assertEquals(1, state.getSourceSlot());
    }

    @Test
    public void restoreReturnsModifiedProjectionToItsOriginalSlot() {
        List<ItemStack> lane = lane(air(), stack(Material.COAL, 8), air());
        State state = new State();
        ItemStack projected = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FUEL_THEN_PRESENT, state
        );
        projected.setAmount(3);

        ItemStack remainder = PassiveLaneProjection.restore(lane, projected, state);

        assertNull(remainder);
        assertEquals(Material.COAL, lane.get(1).getType());
        assertEquals(3, lane.get(1).getAmount());
        assertEquals(-1, state.getSourceSlot());
    }

    @Test
    public void existingNativeStackDoesNotDeleteIdenticalHiddenStacks() {
        List<ItemStack> lane = lane(stack(Material.DIAMOND, 32), stack(Material.DIAMOND, 16));
        State state = new State();
        ItemStack nativeStack = stack(Material.DIAMOND, 32);

        ItemStack projected = PassiveLaneProjection.begin(
            lane, nativeStack, SelectionMode.FIRST_PRESENT, state
        );

        assertEquals(nativeStack, projected);
        assertEquals(32, lane.get(0).getAmount());
        assertEquals(16, lane.get(1).getAmount());
        assertEquals(-1, state.getSourceSlot());
    }

    @Test
    public void repeatedDisableEnableRoundTripDoesNotDuplicate() {
        List<ItemStack> lane = lane(stack(Material.IRON_ORE, 12), air());
        State state = new State();

        ItemStack firstProjection = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        );
        assertNull(PassiveLaneProjection.restore(lane, firstProjection, state));
        ItemStack secondProjection = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        );
        assertNull(PassiveLaneProjection.restore(lane, secondProjection, state));

        assertEquals(Material.IRON_ORE, lane.get(0).getType());
        assertEquals(12, lane.get(0).getAmount());
        assertEquals(Material.AIR, lane.get(1).getType());
    }

    private static List<ItemStack> lane(ItemStack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    private static ItemStack air() {
        return stack(Material.AIR, 1);
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
