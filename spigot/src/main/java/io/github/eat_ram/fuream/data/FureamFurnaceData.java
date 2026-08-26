package io.github.eat_ram.fuream.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBTCompoundList;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import de.tr7zw.nbtapi.iface.ReadableNBTList;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import io.github.eat_ram.fuream.util.NbtUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
    public final @NotNull HashMap<@NotNull KeyableItemStack, @NotNull NamespacedKey> overriddenRecipes;
    public @Nullable NamespacedKey runningRecipe;
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
            !this.recipeOverridingInput.getType().isAir()) return true;
        if (!this.overriddenRecipes.isEmpty() || this.runningRecipe != null) return true;
        for (ItemStack s : this.inputs) {
            if (!s.getType().isAir() && s.getAmount() > 0) return true;
        }
        for (ItemStack s : this.fuels) {
            if (!s.getType().isAir() && s.getAmount() > 0) return true;
        }
        for (ItemStack s : this.outputs) {
            if (!s.getType().isAir() && s.getAmount() > 0) return true;
        }
        return false;
    }

    @Override
    public void writeNbt(FureamDataHolder holder, @NotNull ReadWriteNBT nbt) {
        NbtUtil.writeStacks(nbt, INPUTS_KEY, this.inputs);
        NbtUtil.writeStacks(nbt, FUELS_KEY, this.fuels);
        NbtUtil.writeStacks(nbt, OUTPUTS_KEY, this.outputs);

        if (nbt.hasTag(RECIPE_OVERRIDING_INPUT_KEY)) {
            nbt.removeKey(RECIPE_OVERRIDING_INPUT_KEY);
        }
        ReadWriteNBT overridingInputTag = nbt.getOrCreateCompound(RECIPE_OVERRIDING_INPUT_KEY);
        if (this.recipeOverridingInput != null && !this.recipeOverridingInput.getType().isAir()) {
            NbtUtil.writeItemToCompound(overridingInputTag, this.recipeOverridingInput);
        }

        ReadWriteNBTCompoundList list = NbtUtil.resetCompoundList(nbt, OVERRIDDEN_RECIPES_KEY);
        for (Map.Entry<KeyableItemStack, NamespacedKey> entry : this.overriddenRecipes.entrySet()) {
            ItemStack stack = entry.getKey().stack;
            if (!stack.getType().isAir()) {
                ReadWriteNBT sub = list.addCompound();
                ReadWriteNBT itemNbt = NBT.itemStackToNBT(stack);
                sub.mergeCompound(itemNbt);
                sub.setString("RecipeId", entry.getValue().toString());
            }
        }

        if (this.runningRecipe != null) {
            nbt.setString(RUNNING_RECIPE_KEY, this.runningRecipe.toString());
        } else if (nbt.hasTag(RUNNING_RECIPE_KEY)) {
            nbt.removeKey(RUNNING_RECIPE_KEY);
        }
        nbt.setFloat(XP_KEY, this.experience);
        if (nbt.hasTag("FureamInitialized")) {
            nbt.removeKey("FureamInitialized");
        }
    }

    @Override
    public void readNbt(FureamDataHolder holder, @NotNull ReadableNBT nbt) {
        for (int i = 0; i < this.inputs.size(); i++) {
            this.inputs.set(i, new ItemStack(Material.AIR));
        }
        for (int i = 0; i < this.fuels.size(); i++) {
            this.fuels.set(i, new ItemStack(Material.AIR));
        }
        for (int i = 0; i < this.outputs.size(); i++) {
            this.outputs.set(i, new ItemStack(Material.AIR));
        }

        if (nbt.hasTag(INPUTS_KEY)) {
            NbtUtil.readStacks(nbt, INPUTS_KEY, this.inputs);
        }
        if (nbt.hasTag(FUELS_KEY)) {
            NbtUtil.readStacks(nbt, FUELS_KEY, this.fuels);
        }
        if (nbt.hasTag(OUTPUTS_KEY)) {
            NbtUtil.readStacks(nbt, OUTPUTS_KEY, this.outputs);
        }
        if (nbt.hasTag(RECIPE_OVERRIDING_INPUT_KEY)) {
            this.recipeOverridingInput = NbtUtil.fromNbt(nbt.getCompound(RECIPE_OVERRIDING_INPUT_KEY));
        } else {
            this.recipeOverridingInput = new ItemStack(Material.AIR);
        }
        this.overriddenRecipes.clear();
        if (nbt.hasTag(OVERRIDDEN_RECIPES_KEY)) {
            ReadableNBTList<ReadWriteNBT> list = nbt.getCompoundList(OVERRIDDEN_RECIPES_KEY);
            for (int i = 0; i < list.size(); ++i) {
                ReadWriteNBT tag = list.get(i);
                if (tag.hasTag("RecipeId")) {
                    String recipeIdStr = tag.getString("RecipeId");
                    NamespacedKey key = NamespacedKey.fromString(recipeIdStr);
                    if (key != null) {
                        ItemStack stack = NbtUtil.fromNbt(tag);
                        if (!stack.getType().isAir()) {
                            stack.setAmount(1);
                            this.overriddenRecipes.put(new KeyableItemStack(stack), key);
                        }
                    }
                }
            }
        }
        this.runningRecipe = nbt.hasTag(RUNNING_RECIPE_KEY)
            ? NamespacedKey.fromString(nbt.getString(RUNNING_RECIPE_KEY))
            : null;
        this.experience = nbt.hasTag(XP_KEY) ? nbt.getFloat(XP_KEY) : 0f;
    }
}
