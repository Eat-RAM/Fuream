package io.github.eat_ram.fuream.logic;

import java.util.Optional;

import io.github.eat_ram.fuream.data.FureamDataHolder;
import net.minecraft.recipe.Recipe;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import org.jetbrains.annotations.Range;

/**
 * The server-side queue smelting engine for the virtual furnace. It owns:
 * <ul>
 *   <li>the per-tick smelting loop (replaces the vanilla tick for vanilla
 *       furnaces, see {@code AbstractFurnaceBlockEntityMixin});</li>
 *   <li>the fuel / recipe checks used by the screen handler;</li>
 *   <li>automation: hoppers / droppers keep talking to the vanilla 3-slot
 *       inventory (slot 0 = input lane, slot 1 = fuel lane, slot 2 =
 *       output lane) exactly like a vanilla furnace, keeping mods such as
 *       lithium happy; every tick the lanes are synchronized with the
 *       virtual rows;</li>
 *   <li>experience payout and item drops on break.</li>
 * </ul>
 *
 * <p>Model per the mod spec: one item is smelted at a time; inputs are
 * taken from the left-most non-empty input slot (row 1), fuel is consumed
 * from the left-most fuel slot (row 3), and the result is placed into the
 * left-most output slot that can accept it (row 5). When the result cannot
 * fit anywhere, smelting pauses until space frees up.</p>
 */
public abstract class FureamFurnaceLogic {
    // ------------------------------------------------------------------
    //  Fuel / recipe queries (also used by the screen handler)
    // ------------------------------------------------------------------

    /** @return whether {@code stack} can be used as furnace fuel. */
    public static boolean isFuel(@NotNull ItemStack stack) {
        return !stack.isEmpty() &&
               AbstractFurnaceBlockEntity.canUseAsFuel(stack);
    }

    /** @return whether {@code stack} matches a smelting recipe. */
    public static boolean isAcceptableInput(
        @Nullable World world, @NotNull ItemStack stack,
        RecipeType<? extends AbstractCookingRecipe> recipeType
    ) {
        return findRecipe(world, stack, recipeType).isPresent();
    }

    /** @return the smelting recipe for {@code stack}, or null. */
    public static <T extends AbstractCookingRecipe> Optional<T> findRecipe(
        @Nullable World world, @NotNull ItemStack stack,
        RecipeType<T> recipeType
    ) {
        return world == null || stack.isEmpty() ? Optional.empty() :
               world.getRecipeManager().getFirstMatch(
            recipeType, singleSlotInventory(stack), world
        );
    }

    // ------------------------------------------------------------------
    //  Experience / drops
    // ------------------------------------------------------------------

    public static void stashExperience(
        @NotNull World world, @NotNull Recipe<?> recipe,
        @NotNull AbstractFurnaceBlockEntity furnace
    ) {
        if (!world.isClient) {
            if (recipe instanceof AbstractCookingRecipe) {
                FureamFurnaceData data =
                ((FureamDataHolder)furnace)
                .getFureamData("fuream", FureamFurnaceData.class);
                if (data != null) {
                    data.experience +=
                    ((AbstractCookingRecipe)recipe).getExperience();
                }
            }
        }
    }

    /**
     * Pays out the stored experience as orbs near {@code player} (called
     * whenever the player removes an output item) and zeroes the counter.
     */
    public static void grantExperience(
        @NotNull PlayerEntity player,
        @NotNull AbstractFurnaceBlockEntity furnace
    ) {
        if (!player.getWorld().isClient) {
            FureamFurnaceData data =
            ((FureamDataHolder)furnace)
            .getFureamData("fuream", FureamFurnaceData.class);
            if (data != null) {
                int amount = (int)data.experience;
                if (amount > 0) {
                    data.experience -= amount;
                    ExperienceOrbEntity.spawn(
                        (ServerWorld)player.getWorld(), player.getPos(), amount
                    );
                    furnace.markDirty();
                }
            }
        }
    }

    /**
     * Spills the virtual rows as item entities and the stored experience as
     * orbs when the furnace block is broken. (The vanilla 3-slot lane is
     * spilled by vanilla's own {@code onStateReplaced}.)
     */
    public static void dropOnBreak(
        @NotNull ServerWorld world, @NotNull BlockPos pos,
        @NotNull AbstractFurnaceBlockEntity furnace
    ) {
        FureamFurnaceData data =
        ((FureamDataHolder)furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            double x = pos.getX() + 0.5;
            double y = pos.getY() + 0.5;
            double z = pos.getZ() + 0.5;
            for (ItemStack i : data.inputs) {
                dropIfPresent(world, x, y, z, i);
            }
            data.inputs.clear();
            for (ItemStack i : data.fuels) {
                dropIfPresent(world, x, y, z, i);
            }
            data.fuels.clear();
            for (ItemStack i : data.outputs) {
                dropIfPresent(world, x, y, z, i);
            }
            data.outputs.clear();
            dropIfPresent(world, x, y, z, data.recipeOverridingInput);
            int amount = (int)Math.floor(data.experience);
            if (amount > 0) {
                data.experience -= amount;
                ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), amount);
            }
        }
    }

    private static void dropIfPresent(
        @NotNull World world, double x, double y, double z,
        @NotNull ItemStack stack
    ) {
        if (!stack.isEmpty()) {
            ItemScatterer.spawn(world, x, y, z, stack);
        }
    }

    /** One-slot read-only {@link Inventory} for recipe matching. */
    @Contract(value = "_ -> new", pure = true)
    public static Inventory singleSlotInventory(ItemStack stack) {
        return new Inventory() {
            @Override
            @Contract(pure = true)
            public @Range(from = 1, to = 1) int size() {
                return 1;
            }

            @Override
            public boolean isEmpty() {
                return stack.isEmpty();
            }

            @Override
            @Contract(pure = true)
            public @NotNull ItemStack getStack(int slot) {
                return slot == 0 ? stack : ItemStack.EMPTY;
            }

            @Override
            @Contract(pure = true)
            public @NotNull ItemStack removeStack(int slot, int amount) {
                return ItemStack.EMPTY;
            }

            @Override
            @Contract(pure = true)
            public @NotNull ItemStack removeStack(int slot) {
                return ItemStack.EMPTY;
            }

            @Override
            @Contract(pure = true)
            public void setStack(int slot, ItemStack stack) {}

            @Override
            @Contract(pure = true)
            public void markDirty() {}

            @Override
            @Contract(value = "_ -> true", pure = true)
            public boolean canPlayerUse(PlayerEntity player) {
                return true;
            }

            @Override
            @Contract(pure = true)
            public void clear() {}
        };
    }

    @Contract("-> fail")
    private FureamFurnaceLogic() {
        throw new UnsupportedOperationException();
    }
}
