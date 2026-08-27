package io.github.eat_ram.fuream.logic;

import java.util.AbstractList;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PassiveLaneProjectionTest {
    @Test
    public void inputAndOutputUseFirstPresentSlot() {
        List<ItemStack> lane = lane(air(), stack(Material.IRON_ORE, 4), stack(Material.GOLD_ORE, 2));
        State state = new State();

        ItemStack projected = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        );

        assertEquals(Material.IRON_ORE, projected.getType());
        assertEquals(4, projected.getAmount());
        assertEquals(1, state.getSourceSlot());
        assertEquals(Material.AIR, lane.get(1).getType());
        assertEquals(Material.GOLD_ORE, lane.get(2).getType());
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
        assertTrue(state.isPreferredFuelExhausted());
    }

    @Test
    public void emptyNativeSlotRefillsFromNextVirtualStack() {
        List<ItemStack> lane = lane(stack(Material.IRON_ORE, 3), stack(Material.GOLD_ORE, 5));
        State state = new State();

        ItemStack first = PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        );
        ItemStack second = PassiveLaneProjection.takeNext(
            lane, SelectionMode.FIRST_PRESENT, state
        );

        assertEquals(Material.IRON_ORE, first.getType());
        assertEquals(Material.GOLD_ORE, second.getType());
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
    public void existingNativeStackDoesNotDeleteIdenticalVirtualStack() {
        List<ItemStack> lane = lane(stack(Material.COAL, 64), stack(Material.COAL, 64));
        State state = new State();
        ItemStack nativeStack = stack(Material.COAL, 64);

        ItemStack projected = PassiveLaneProjection.begin(
            lane, nativeStack, SelectionMode.FUEL_THEN_PRESENT, state
        );

        assertEquals(nativeStack, projected);
        assertEquals(64, lane.get(0).getAmount());
        assertEquals(64, lane.get(1).getAmount());
        assertEquals(-1, state.getSourceSlot());
    }

    @Test
    public void nonFuelRemainderCanBeReturnedBeforeSelectingHiddenFuel() {
        List<ItemStack> lane = lane(air(), stack(Material.COAL, 4));
        State state = new State();
        ItemStack bucket = stack(Material.BUCKET, 1);
        PassiveLaneProjection.begin(
            lane, bucket, SelectionMode.FUEL_THEN_PRESENT, state
        );

        assertTrue(PassiveLaneProjection.hasPreferredFuel(lane, state));
        assertNull(PassiveLaneProjection.restore(lane, bucket, state));
        ItemStack projected = PassiveLaneProjection.takeNext(
            lane, SelectionMode.FUEL_THEN_PRESENT, state
        );

        assertEquals(Material.COAL, projected.getType());
        assertEquals(Material.BUCKET, lane.get(0).getType());
    }

    @Test
    public void exhaustedLaneIsNotScannedAgain() {
        CountingList lane = new CountingList(lane(air(), air(), air()));
        State state = new State();

        assertNull(PassiveLaneProjection.begin(
            lane, null, SelectionMode.FIRST_PRESENT, state
        ));
        assertTrue(state.isExhausted());
        assertTrue(lane.reads > 0);

        lane.reads = 0;
        assertNull(PassiveLaneProjection.takeNext(
            lane, SelectionMode.FIRST_PRESENT, state
        ));
        assertEquals(0, lane.reads);
    }

    @Test
    public void missingPreferredFuelIsScannedOnlyOnceForStableNativeRemainder() {
        CountingList lane = new CountingList(lane(air(), air(), air()));
        State state = new State();
        PassiveLaneProjection.begin(
            lane, stack(Material.BUCKET, 1), SelectionMode.FUEL_THEN_PRESENT, state
        );

        assertFalse(PassiveLaneProjection.hasPreferredFuel(lane, state));
        assertTrue(lane.reads > 0);

        lane.reads = 0;
        assertFalse(PassiveLaneProjection.hasPreferredFuel(lane, state));
        assertEquals(0, lane.reads);
    }

    @Test
    public void blockedFuelSwitchDoesNotRescanUntilNativeSlotEmpties() {
        CountingList lane = new CountingList(lane(stack(Material.COAL, 64)));
        State state = new State();
        ItemStack bucket = stack(Material.BUCKET, 1);
        PassiveLaneProjection.begin(
            lane, bucket, SelectionMode.FUEL_THEN_PRESENT, state
        );
        assertTrue(PassiveLaneProjection.hasPreferredFuel(lane, state));
        assertEquals(1, PassiveLaneProjection.restore(lane, bucket, state).getAmount());
        state.blockPreferredFuelSwitch();

        lane.reads = 0;
        assertFalse(PassiveLaneProjection.hasPreferredFuel(lane, state));
        assertEquals(0, lane.reads);

        ItemStack projected = PassiveLaneProjection.takeNext(
            lane, SelectionMode.FUEL_THEN_PRESENT, state
        );
        assertEquals(Material.COAL, projected.getType());
        assertFalse(state.isPreferredFuelExhausted());
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

    private static final class CountingList extends AbstractList<ItemStack> {
        private final List<ItemStack> delegate;
        private int reads;

        private CountingList(List<ItemStack> delegate) {
            this.delegate = delegate;
        }

        @Override
        public ItemStack get(int index) {
            this.reads++;
            return this.delegate.get(index);
        }

        @Override
        public ItemStack set(int index, ItemStack element) {
            return this.delegate.set(index, element);
        }

        @Override
        public int size() {
            return this.delegate.size();
        }
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
