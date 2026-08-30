package io.github.eat_ram.fuream.compat;

import java.util.List;

import io.github.eat_ram.fuream.api.FurnaceType;
import org.bukkit.inventory.ItemStack;

public final class RecipeCompat {
    public static RecipeHandle findFirst(ItemStack input, FurnaceType type) {
        return RecipeIndex.findFirst(input, type);
    }

    public static List<RecipeHandle> findAll(ItemStack input, FurnaceType type) {
        return RecipeIndex.findAll(input, type);
    }

    public static RecipeHandle findById(ItemStack input, FurnaceType type, String id) {
        return RecipeIndex.findById(input, type, id);
    }

    public static void rebuild() {
        RecipeIndex.rebuild();
    }

    public static int indexedRecipeCount() {
        return RecipeIndex.size();
    }

    private RecipeCompat() {
    }
}
