package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.eat_ram.fuream.api.FurnaceType;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Immutable cooking recipe snapshot rebuilt only when registrations may have changed. */
public final class RecipeIndex {
    private interface Matcher {
        boolean matches(ItemStack input);
    }

    private static final class Entry {
        private final RecipeHandle handle;
        private final Matcher matcher;

        private Entry(RecipeHandle handle, Matcher matcher) {
            this.handle = handle;
            this.matcher = matcher;
        }
    }

    private static volatile RecipeIndex current = empty();

    private final EnumMap<FurnaceType, List<Entry>> byType;
    private final EnumMap<FurnaceType, Map<String, List<Entry>>> byId;
    private final int size;

    private RecipeIndex(
        EnumMap<FurnaceType, List<Entry>> byType,
        EnumMap<FurnaceType, Map<String, List<Entry>>> byId,
        int size
    ) {
        this.byType = byType;
        this.byId = byId;
        this.size = size;
    }

    public static void rebuild() {
        EnumMap<FurnaceType, List<Entry>> byType = new EnumMap<>(FurnaceType.class);
        EnumMap<FurnaceType, Map<String, List<Entry>>> byId = new EnumMap<>(FurnaceType.class);
        for (FurnaceType type : FurnaceType.values()) {
            byType.put(type, new ArrayList<Entry>());
            byId.put(type, new HashMap<String, List<Entry>>());
        }

        int count = 0;
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            FurnaceType type = furnaceType(recipe);
            if (type == null) continue;
            Entry entry = snapshot(recipe, type);
            if (entry == null) continue;
            byType.get(type).add(entry);
            List<Entry> sameId = byId.get(type).get(entry.handle.id);
            if (sameId == null) {
                sameId = new ArrayList<>();
                byId.get(type).put(entry.handle.id, sameId);
            }
            sameId.add(entry);
            count++;
        }

        for (FurnaceType type : FurnaceType.values()) {
            byType.put(type, Collections.unmodifiableList(byType.get(type)));
            Map<String, List<Entry>> immutableIds = new HashMap<>();
            for (Map.Entry<String, List<Entry>> entry : byId.get(type).entrySet()) {
                immutableIds.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
            }
            byId.put(type, Collections.unmodifiableMap(immutableIds));
        }
        current = new RecipeIndex(byType, byId, count);
    }

    public static @Nullable RecipeHandle findFirst(ItemStack input, FurnaceType type) {
        if (ItemCompat.isEmpty(input)) return null;
        for (Entry entry : current.byType.get(type)) {
            if (entry.matcher.matches(input)) return entry.handle;
        }
        return null;
    }

    public static @NotNull List<RecipeHandle> findAll(ItemStack input, FurnaceType type) {
        List<RecipeHandle> result = new ArrayList<>();
        if (ItemCompat.isEmpty(input)) return result;
        for (Entry entry : current.byType.get(type)) {
            if (entry.matcher.matches(input)) result.add(entry.handle);
        }
        result.sort(Comparator.comparing(handle -> handle.id));
        return result;
    }

    public static @Nullable RecipeHandle findById(ItemStack input, FurnaceType type, String id) {
        if (id == null || ItemCompat.isEmpty(input)) return null;
        List<Entry> entries = current.byId.get(type).get(id);
        if (entries == null) return null;
        for (Entry entry : entries) {
            if (entry.matcher.matches(input)) return entry.handle;
        }
        return null;
    }

    public static int size() {
        return current.size;
    }

    private static RecipeIndex empty() {
        EnumMap<FurnaceType, List<Entry>> byType = new EnumMap<>(FurnaceType.class);
        EnumMap<FurnaceType, Map<String, List<Entry>>> byId = new EnumMap<>(FurnaceType.class);
        for (FurnaceType type : FurnaceType.values()) {
            byType.put(type, Collections.<Entry>emptyList());
            byId.put(type, Collections.<String, List<Entry>>emptyMap());
        }
        return new RecipeIndex(byType, byId, 0);
    }

    private static @Nullable FurnaceType furnaceType(Recipe recipe) {
        String simpleName = recipe.getClass().getSimpleName();
        if (simpleName.endsWith("SmokingRecipe")) return FurnaceType.SMOKER;
        if (simpleName.endsWith("BlastingRecipe")) return FurnaceType.BLAST_FURNACE;
        return simpleName.endsWith("FurnaceRecipe") ? FurnaceType.FURNACE : null;
    }

    private static @Nullable Entry snapshot(Recipe recipe, FurnaceType type) {
        Matcher matcher = matcher(recipe);
        if (matcher == null) return null;
        ItemStack output = recipe.getResult() == null ? ItemCompat.empty() : recipe.getResult().clone();
        int cookingTime = number(recipe, "getCookingTime", type == FurnaceType.FURNACE ? 200 : 100).intValue();
        float experience = number(recipe, "getExperience", 0f).floatValue();
        String id = recipeId(recipe, type, output);
        return new Entry(new RecipeHandle(
            recipe, id, output, Math.max(1, cookingTime), experience
        ), matcher);
    }

    private static @Nullable Matcher matcher(Recipe recipe) {
        try {
            Method choiceGetter = recipe.getClass().getMethod("getInputChoice");
            Object choice = choiceGetter.invoke(recipe);
            Method choicesGetter = choice.getClass().getMethod("getChoices");
            Object rawChoices = choicesGetter.invoke(choice);
            if (rawChoices instanceof List) {
                final List<Object> choices = new ArrayList<>((List<?>) rawChoices);
                return input -> matchesChoiceSnapshot(choices, input);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        try {
            Method inputGetter = recipe.getClass().getMethod("getInput");
            ItemStack value = (ItemStack) inputGetter.invoke(recipe);
            if (value == null) return null;
            final ItemStack expected = value.clone();
            return input -> expected.getType() == input.getType() &&
                (expected.getDurability() == Short.MAX_VALUE || expected.getDurability() == input.getDurability());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean matchesChoiceSnapshot(List<Object> choices, ItemStack input) {
        for (Object choice : choices) {
            if (choice instanceof org.bukkit.Material && choice == input.getType()) return true;
            if (choice instanceof ItemStack && ((ItemStack) choice).isSimilar(input)) return true;
        }
        return false;
    }

    private static Number number(Recipe recipe, String methodName, Number fallback) {
        try {
            Object value = recipe.getClass().getMethod(methodName).invoke(recipe);
            return value instanceof Number ? (Number) value : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    private static String recipeId(Recipe recipe, FurnaceType type, ItemStack output) {
        try {
            Object key = recipe.getClass().getMethod("getKey").invoke(recipe);
            if (key != null) return key.toString();
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return "legacy:" + type.name().toLowerCase(Locale.ROOT) + '/' +
            output.getType().name().toLowerCase(Locale.ROOT) + '/' + output.getDurability();
    }

}
