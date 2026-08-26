package io.github.eat_ram.fuream.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.nbt.NativeItemNbt;
import io.github.eat_ram.fuream.nbt.NativeNbtCompound;
import io.github.eat_ram.fuream.nbt.NativeNbtList;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import io.github.eat_ram.fuream.util.NbtUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.eat_ram.fuream.CollectionUtil.newDefaultedArrayList;

/**
 * The server-side smelting state of a virtual furnace: inputs, fuels, outputs,
 * recipe overrides, and accumulated experience.
 */
public class FureamFurnaceData implements FureamData {
    public static final @NotNull String INPUTS_KEY = "Inputs";
    public static final @NotNull String FUELS_KEY = "Fuels";
    public static final @NotNull String OUTPUTS_KEY = "Outputs";
    public static final @NotNull String RECIPE_OVERRIDING_INPUT_KEY = "RecipeOverridingInput";
    public static final @NotNull String OVERRIDDEN_RECIPES_KEY = "OverriddenRecipes";
    public static final @NotNull String RUNNING_RECIPE_KEY = "RunningRecipe";
    public static final @NotNull String XP_KEY = "Xp";

    public final @NotNull ArrayList<@NotNull ItemStack> inputs;
    public final @NotNull ArrayList<@NotNull ItemStack> fuels;
    public final @NotNull ArrayList<@NotNull ItemStack> outputs;
    public @NotNull ItemStack recipeOverridingInput;
    public final @NotNull HashMap<@NotNull KeyableItemStack, @NotNull String> overriddenRecipes;
    public @Nullable String runningRecipe;
    public float experience;

    public FureamFurnaceData() {
        this.inputs = newDefaultedArrayList(new ItemStack(Material.AIR), 1);
        this.fuels = newDefaultedArrayList(new ItemStack(Material.AIR), 1);
        this.outputs = newDefaultedArrayList(new ItemStack(Material.AIR), 1);
        this.recipeOverridingInput = new ItemStack(Material.AIR);
        this.overriddenRecipes = new HashMap<>();
        this.runningRecipe = null;
    }

    public boolean hasAny() {
        if (this.experience > 0f) return true;
        if (this.recipeOverridingInput != null &&
            !ItemCompat.isEmpty(this.recipeOverridingInput)) return true;
        if (!this.overriddenRecipes.isEmpty() || this.runningRecipe != null) return true;
        for (ItemStack s : this.inputs) {
            if (!ItemCompat.isEmpty(s)) return true;
        }
        for (ItemStack s : this.fuels) {
            if (!ItemCompat.isEmpty(s)) return true;
        }
        for (ItemStack s : this.outputs) {
            if (!ItemCompat.isEmpty(s)) return true;
        }
        return false;
    }

    @Override
    public void writeNbt(FureamDataHolder holder, @NotNull NativeNbtCompound nbt) {
        if (!this.hasAny()) return;
        NbtUtil.writeStacks(nbt, INPUTS_KEY, this.inputs);
        NbtUtil.writeStacks(nbt, FUELS_KEY, this.fuels);
        NbtUtil.writeStacks(nbt, OUTPUTS_KEY, this.outputs);

        if (nbt.has(RECIPE_OVERRIDING_INPUT_KEY)) {
            nbt.remove(RECIPE_OVERRIDING_INPUT_KEY);
        }
        NativeNbtCompound overridingInputTag = nbt.getOrCreateCompound(RECIPE_OVERRIDING_INPUT_KEY);
        if (!ItemCompat.isEmpty(this.recipeOverridingInput)) {
            NbtUtil.writeItemToCompound(overridingInputTag, this.recipeOverridingInput);
        }

        NativeNbtList list = NbtUtil.resetCompoundList(nbt, OVERRIDDEN_RECIPES_KEY);
        for (Map.Entry<KeyableItemStack, String> entry : this.overriddenRecipes.entrySet()) {
            ItemStack stack = entry.getKey().stack;
            if (!ItemCompat.isEmpty(stack)) {
                NativeNbtCompound sub = NativeItemNbt.write(stack);
                sub.setString("RecipeId", entry.getValue());
                list.add(sub);
            }
        }

        if (this.runningRecipe != null) {
            nbt.setString(RUNNING_RECIPE_KEY, this.runningRecipe);
        } else if (nbt.has(RUNNING_RECIPE_KEY)) {
            nbt.remove(RUNNING_RECIPE_KEY);
        }
        nbt.setFloat(XP_KEY, this.experience);
        if (nbt.has("FureamInitialized")) {
            nbt.remove("FureamInitialized");
        }
    }

    @Override
    public void readNbt(FureamDataHolder holder, @NotNull NativeNbtCompound nbt) {
        for (int i = 0; i < this.inputs.size(); i++) {
            this.inputs.set(i, new ItemStack(Material.AIR));
        }
        for (int i = 0; i < this.fuels.size(); i++) {
            this.fuels.set(i, new ItemStack(Material.AIR));
        }
        for (int i = 0; i < this.outputs.size(); i++) {
            this.outputs.set(i, new ItemStack(Material.AIR));
        }

        if (nbt.has(INPUTS_KEY)) {
            NbtUtil.readStacks(nbt, INPUTS_KEY, this.inputs);
        }
        if (nbt.has(FUELS_KEY)) {
            NbtUtil.readStacks(nbt, FUELS_KEY, this.fuels);
        }
        if (nbt.has(OUTPUTS_KEY)) {
            NbtUtil.readStacks(nbt, OUTPUTS_KEY, this.outputs);
        }
        if (nbt.has(RECIPE_OVERRIDING_INPUT_KEY)) {
            this.recipeOverridingInput = NbtUtil.fromNbt(nbt.getCompound(RECIPE_OVERRIDING_INPUT_KEY));
        } else {
            this.recipeOverridingInput = new ItemStack(Material.AIR);
        }
        this.overriddenRecipes.clear();
        if (nbt.has(OVERRIDDEN_RECIPES_KEY)) {
            NativeNbtList list = nbt.getList(OVERRIDDEN_RECIPES_KEY);
            if (list != null) {
                for (int i = 0; i < list.size(); ++i) {
                    NativeNbtCompound tag = list.getCompound(i);
                    if (tag != null && tag.has("RecipeId")) {
                        String recipeIdStr = tag.getString("RecipeId");
                        if (!recipeIdStr.isEmpty()) {
                            ItemStack stack = NbtUtil.fromNbt(tag);
                            if (!ItemCompat.isEmpty(stack)) {
                                stack.setAmount(1);
                                this.overriddenRecipes.put(new KeyableItemStack(stack), recipeIdStr);
                            }
                        }
                    }
                }
            }
        }
        this.runningRecipe = nbt.has(RUNNING_RECIPE_KEY)
            ? nbt.getString(RUNNING_RECIPE_KEY)
            : null;
        this.experience = nbt.has(XP_KEY) ? nbt.getFloat(XP_KEY, 0f) : 0f;
    }
}
