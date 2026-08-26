package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import io.github.eat_ram.fuream.api.FurnaceType;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

public final class RecipeCompat {
    public static RecipeHandle findFirst(ItemStack input, FurnaceType type) {
        if (ItemCompat.isEmpty(input)) return null;
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (matches(recipe, input, type)) return snapshot(recipe, type);
        }
        return null;
    }

    public static List<RecipeHandle> findAll(ItemStack input, FurnaceType type) {
        List<RecipeHandle> result = new ArrayList<>();
        if (ItemCompat.isEmpty(input)) return result;
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (matches(recipe, input, type)) result.add(snapshot(recipe, type));
        }
        result.sort(Comparator.comparing(handle -> handle.id));
        return result;
    }

    public static RecipeHandle findById(ItemStack input, FurnaceType type, String id) {
        if (id == null) return null;
        for (RecipeHandle handle : findAll(input, type)) {
            if (id.equals(handle.id)) return handle;
        }
        return null;
    }

    private static boolean matches(Recipe recipe, ItemStack input, FurnaceType type) {
        String simpleName = recipe.getClass().getSimpleName();
        if (type == FurnaceType.SMOKER) {
            if (!simpleName.endsWith("SmokingRecipe")) return false;
        } else if (type == FurnaceType.BLAST_FURNACE) {
            if (!simpleName.endsWith("BlastingRecipe")) return false;
        } else if (!simpleName.endsWith("FurnaceRecipe")) {
            return false;
        }

        try {
            Method choiceMethod = recipe.getClass().getMethod("getInputChoice");
            Object choice = choiceMethod.invoke(recipe);
            Method test = choice.getClass().getMethod("test", ItemStack.class);
            return Boolean.TRUE.equals(test.invoke(choice, input));
        } catch (ReflectiveOperationException ignored) {
            try {
                Method inputMethod = recipe.getClass().getMethod("getInput");
                ItemStack expected = (ItemStack) inputMethod.invoke(recipe);
                if (expected == null || expected.getType() != input.getType()) return false;
                short durability = expected.getDurability();
                return durability == Short.MAX_VALUE || durability == input.getDurability();
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
    }

    private static RecipeHandle snapshot(Recipe recipe, FurnaceType type) {
        ItemStack output = recipe.getResult() == null ? ItemCompat.empty() : recipe.getResult().clone();
        int cookingTime = type == FurnaceType.FURNACE ? 200 : 100;
        float experience = 0f;
        try {
            Object value = recipe.getClass().getMethod("getCookingTime").invoke(recipe);
            if (value instanceof Number) cookingTime = ((Number) value).intValue();
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Object value = recipe.getClass().getMethod("getExperience").invoke(recipe);
            if (value instanceof Number) experience = ((Number) value).floatValue();
        } catch (ReflectiveOperationException ignored) {
        }

        String id = null;
        try {
            Object key = recipe.getClass().getMethod("getKey").invoke(recipe);
            if (key != null) id = key.toString();
        } catch (ReflectiveOperationException ignored) {
        }
        if (id == null) {
            id = "legacy:" + type.name().toLowerCase(LocaleHolder.ROOT) + "/" +
                output.getType().name().toLowerCase(LocaleHolder.ROOT) + "/" + output.getDurability();
        }
        return new RecipeHandle(recipe, id, output, Math.max(1, cookingTime), experience);
    }

    /** Avoids repeatedly resolving Locale from old JVM implementations. */
    private static final class LocaleHolder {
        private static final java.util.Locale ROOT = java.util.Locale.ROOT;
    }

    private RecipeCompat() {
        throw new UnsupportedOperationException();
    }
}
