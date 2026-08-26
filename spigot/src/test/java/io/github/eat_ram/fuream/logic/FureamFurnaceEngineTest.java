package io.github.eat_ram.fuream.logic;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import io.github.eat_ram.fuream.compat.ComparatorCompat;
import io.github.eat_ram.fuream.compat.RecipeHandle;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.util.FurnacePos;
import io.github.eat_ram.fuream.util.KeyableItemStack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void resultSpreadsAcrossCompatibleOutputSlots() {
        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(stack(Material.IRON_INGOT, 63));
        outputs.add(stack(Material.AIR, 1));

        ItemStack remainder = FureamFurnaceEngine.insertStackIntoList(
            outputs, stack(Material.IRON_INGOT, 3), 2
        );

        assertEquals(64, outputs.get(0).getAmount());
        assertEquals(2, outputs.get(1).getAmount());
        assertEquals(Material.AIR, remainder.getType());
    }

    @Test
    public void wetSpongeDoesNotConvertBucketWhenFuelRegionIsFull() {
        List<ItemStack> fuels = new ArrayList<>();
        fuels.add(stack(Material.BUCKET, 1));
        fuels.add(stack(Material.COAL, 1));

        FureamFurnaceEngine.handleWetSponge(stack(Material.WET_SPONGE, 1), fuels, 2);

        assertEquals(Material.BUCKET, fuels.get(0).getType());
        assertEquals(1, fuels.get(0).getAmount());
        assertEquals(Material.COAL, fuels.get(1).getType());
    }

    @Test
    public void wetSpongeConvertsBucketWhenFuelRegionHasAnEmptySlot() {
        List<ItemStack> fuels = new ArrayList<>();
        fuels.add(stack(Material.BUCKET, 1));
        fuels.add(stack(Material.AIR, 1));

        FureamFurnaceEngine.handleWetSponge(stack(Material.WET_SPONGE, 1), fuels, 2);

        assertEquals(Material.AIR, fuels.get(0).getType());
        assertEquals(Material.WATER_BUCKET, fuels.get(1).getType());
        assertEquals(2, fuels.size());
    }

    @Test
    public void comparatorUsesOnlyTheThreeMaskedStacks() {
        FureamFurnaceData data = new FureamFurnaceData();
        data.inputs.set(0, stack(Material.IRON_ORE, 64));
        data.inputs.add(stack(Material.GOLD_ORE, 64));
        data.fuels.set(0, stack(Material.COAL, 64));
        data.outputs.set(0, stack(Material.IRON_INGOT, 64));

        assertEquals(15, ComparatorCompat.calculate(data));
    }

    @Test
    public void legacyDurabilityParticipatesInRecipeOverrideIdentity() {
        ItemStack first = new TestItemStack(Material.LEGACY_WOOL, 1, (short) 1);
        ItemStack second = new TestItemStack(Material.LEGACY_WOOL, 1, (short) 2);

        assertNotEquals(new KeyableItemStack(first), new KeyableItemStack(second));
    }

    @Test
    public void changingPreferredSlotOrItemResetsRecipeProgress() {
        FurnaceContext context = new FurnaceContext(
            new FurnacePos("test", 0, 64, 0), FurnaceType.FURNACE
        );
        ItemStack iron = stack(Material.IRON_ORE, 8);
        assertTrue(FureamFurnaceEngine.updateInputIdentity(context, 0, iron));

        context.cookTime = 41;
        context.data.runningRecipe = "minecraft:iron_ingot";
        context.startSmeltPending = false;
        assertFalse(FureamFurnaceEngine.updateInputIdentity(context, 0, iron.clone()));
        assertEquals(41, context.cookTime);

        assertTrue(FureamFurnaceEngine.updateInputIdentity(context, 1, iron.clone()));
        assertEquals(0, context.cookTime);
        assertNull(context.data.runningRecipe);
        assertTrue(context.startSmeltPending);

        context.cookTime = 12;
        context.data.runningRecipe = "minecraft:iron_ingot";
        assertTrue(FureamFurnaceEngine.updateInputIdentity(
            context, 1, stack(Material.GOLD_ORE, 1)
        ));
        assertEquals(0, context.cookTime);
        assertNull(context.data.runningRecipe);
    }

    @Test
    public void recipeExperienceAccumulatesWithoutRounding() {
        FureamFurnaceData data = new FureamFurnaceData();
        data.experience = 1.25f;
        RecipeHandle recipe = new RecipeHandle(
            null, "test:recipe", stack(Material.IRON_INGOT, 1), 200, 0.7f
        );

        FureamFurnaceLogic.stashExperience(recipe, data);

        assertEquals(1.95f, data.experience, 0.0001f);
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
        private short testDurability;

        private TestItemStack(Material material, int amount) {
            super(material, amount);
        }

        private TestItemStack(Material material, int amount, short durability) {
            super(material, amount, durability);
            this.testDurability = durability;
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
        public short getDurability() {
            return this.testDurability;
        }

        @Override
        public void setDurability(short durability) {
            this.testDurability = durability;
        }

        @Override
        public ItemStack clone() {
            return new TestItemStack(this.getType(), this.getAmount(), this.getDurability());
        }
    }
}
