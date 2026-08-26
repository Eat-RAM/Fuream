package io.github.eat_ram.fuream.compat;

import org.bukkit.inventory.ItemStack;

/** Version-neutral cooking recipe snapshot. */
public final class RecipeHandle {
    public final Object nativeRecipe;
    public final String id;
    public final ItemStack result;
    public final int cookingTime;
    public final float experience;

    public RecipeHandle(Object nativeRecipe, String id, ItemStack result, int cookingTime, float experience) {
        this.nativeRecipe = nativeRecipe;
        this.id = id;
        this.result = result;
        this.cookingTime = cookingTime;
        this.experience = experience;
    }
}
