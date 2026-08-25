package io.github.eat_ram.fuream.logic;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FureamFurnaceLogic {
    public static boolean isFuel(@Nullable ItemStack stack) {
        return FuelTable.isFuel(stack);
    }

    public static boolean isAcceptableInput(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        return findRecipe(world, stack, furnaceType).isPresent();
    }

    public static Optional<CookingRecipe<?>> findRecipe(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        if (stack.getType().isAir()) {
            return Optional.empty();
        }
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (isRecipeMatchForFurnace(r, furnaceType, stack)) {
                return Optional.of((CookingRecipe<?>) r);
            }
        }
        return Optional.empty();
    }

    public static List<CookingRecipe<?>> findAllMatches(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        List<CookingRecipe<?>> list = new ArrayList<>();
        if (stack.getType().isAir()) {
            return list;
        }
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (isRecipeMatchForFurnace(r, furnaceType, stack)) {
                list.add((CookingRecipe<?>) r);
            }
        }
        return list;
    }

    private static boolean isRecipeMatchForFurnace(Recipe r, FurnaceType furnaceType, ItemStack stack) {
        if (!(r instanceof CookingRecipe)) {
            return false;
        }
        CookingRecipe<?> cr = (CookingRecipe<?>) r;
        if (furnaceType == FurnaceType.SMOKER) {
            if (!(cr instanceof SmokingRecipe)) return false;
        } else if (furnaceType == FurnaceType.BLAST_FURNACE) {
            if (!(cr instanceof BlastingRecipe)) return false;
        } else {
            if (!(cr instanceof FurnaceRecipe)) return false;
        }
        return cr.getInputChoice().test(stack);
    }

    public static void stashExperience(
        @Nullable CookingRecipe<?> recipe, @NotNull FureamFurnaceData data
    ) {
        if (recipe != null) {
            data.experience += recipe.getExperience();
        }
    }

    public static void grantExperience(
        @NotNull Player player, @NotNull Location loc, @NotNull FureamFurnaceData data
    ) {
        int amount = (int) data.experience;
        if (amount > 0) {
            data.experience -= amount;
            World world = player.getWorld();
            world.spawn(player.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(amount));
        }
    }

    public static void dropOnBreak(
        @NotNull Location pos, @NotNull FureamFurnaceData data
    ) {
        World world = pos.getWorld();
        if (world == null) return;
        Location center = pos.clone().add(0.5, 0.5, 0.5);

        for (ItemStack s : data.inputs) {
            dropIfPresent(world, center, s);
        }
        data.inputs.clear();

        for (ItemStack s : data.fuels) {
            dropIfPresent(world, center, s);
        }
        data.fuels.clear();

        for (ItemStack s : data.outputs) {
            dropIfPresent(world, center, s);
        }
        data.outputs.clear();

        if (data.recipeOverridingInput != null && !data.recipeOverridingInput.getType().isAir()) {
            dropIfPresent(world, center, data.recipeOverridingInput);
            data.recipeOverridingInput = new ItemStack(Material.AIR);
        }

        int amount = (int) Math.floor(data.experience);
        if (amount > 0) {
            data.experience -= amount;
            world.spawn(center, ExperienceOrb.class, orb -> orb.setExperience(amount));
        }
    }

    private static void dropIfPresent(@NotNull World world, @NotNull Location loc, @Nullable ItemStack stack) {
        if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
            world.dropItemNaturally(loc, stack.clone());
        }
    }

    private FureamFurnaceLogic() {
        throw new UnsupportedOperationException();
    }
}
