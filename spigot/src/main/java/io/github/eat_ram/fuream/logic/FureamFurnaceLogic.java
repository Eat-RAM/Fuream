package io.github.eat_ram.fuream.logic;

import java.util.List;
import java.util.Optional;

import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.RecipeCompat;
import io.github.eat_ram.fuream.compat.RecipeHandle;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FureamFurnaceLogic {
    public static boolean isFuel(@Nullable ItemStack stack) {
        return FuelTable.isFuel(stack);
    }

    public static boolean isAcceptableInput(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        return RecipeCompat.findFirst(stack, furnaceType) != null;
    }

    public static Optional<RecipeHandle> findRecipe(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        return Optional.ofNullable(RecipeCompat.findFirst(stack, furnaceType));
    }

    public static List<RecipeHandle> findAllMatches(
        @Nullable World world, @NotNull ItemStack stack, @NotNull FurnaceType furnaceType
    ) {
        return RecipeCompat.findAll(stack, furnaceType);
    }

    public static void stashExperience(
        @Nullable RecipeHandle recipe, @NotNull FureamFurnaceData data
    ) {
        if (recipe != null) data.experience += recipe.experience;
    }

    public static void grantExperience(
        @NotNull Player player, @NotNull Location loc, @NotNull FureamFurnaceData data
    ) {
        int amount = (int) data.experience;
        if (amount <= 0) return;
        data.experience -= amount;
        ExperienceOrb orb = player.getWorld().spawn(player.getLocation(), ExperienceOrb.class);
        orb.setExperience(amount);
    }

    public static void dropOnBreak(
        @NotNull Location pos, @NotNull FureamFurnaceData data
    ) {
        World world = pos.getWorld();
        if (world == null) return;
        Location center = pos.clone().add(0.5, 0.5, 0.5);

        for (ItemStack stack : data.inputs) dropIfPresent(world, center, stack);
        data.inputs.clear();
        for (ItemStack stack : data.fuels) dropIfPresent(world, center, stack);
        data.fuels.clear();
        for (ItemStack stack : data.outputs) dropIfPresent(world, center, stack);
        data.outputs.clear();

        // Deliberately retained for Fabric parity (known Fabric bug).
        if (!ItemCompat.isEmpty(data.recipeOverridingInput)) {
            dropIfPresent(world, center, data.recipeOverridingInput);
            data.recipeOverridingInput = ItemCompat.empty();
        }

        int amount = (int) Math.floor(data.experience);
        if (amount > 0) {
            data.experience -= amount;
            ExperienceOrb orb = world.spawn(center, ExperienceOrb.class);
            orb.setExperience(amount);
        }
    }

    private static void dropIfPresent(World world, Location location, ItemStack stack) {
        if (!ItemCompat.isEmpty(stack)) world.dropItemNaturally(location, stack.clone());
    }

    private FureamFurnaceLogic() {
    }
}
