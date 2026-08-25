package io.github.eat_ram.fuream.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.eat_ram.fuream.util.KeyableItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.github.eat_ram.fuream.CollectionUtil.newDefaultedArrayList;

/**
 * The entire server-side smelting state of a virtual furnace: 9 inputs,
 * 9 fuels, 9 outputs plus the burning/progress/experience bookkeeping.
 * Persisted under {@code FureamData} on the block entity; the decorative
 * glass rows of the GUI are <em>not</em> part of this state.
 */
public class FureamFurnaceData implements FureamData {
    public static final @NotNull String INPUTS_KEY = "Inputs";
    public static final @NotNull String FUELS_KEY = "Fuels";
    public static final @NotNull String OUTPUTS_KEY = "Outputs";
    public static final @NotNull String RECIPE_OVERRIDING_INPUT_KEY =
    "RecipeOverridingInput";
    public static final @NotNull String OVERRIDDEN_RECIPES_KEY =
    "OverriddenRecipes";
    public static final @NotNull String RUNNING_RECIPE_KEY = "RunningRecipe";
    public static final @NotNull String XP_KEY = "Xp";

    public final @NotNull ArrayList<@NotNull ItemStack> inputs;
    public final @NotNull ArrayList<@NotNull ItemStack> fuels;
    public final @NotNull ArrayList<@NotNull ItemStack> outputs;
    public @NotNull ItemStack recipeOverridingInput;
    public final @NotNull HashMap<
        @NotNull KeyableItemStack, @NotNull Identifier
    > overriddenRecipes;
    public @Nullable Identifier runningRecipe;
    public float experience;

    public FureamFurnaceData() {
        this.inputs = newDefaultedArrayList(ItemStack.EMPTY, 1);
        this.fuels = newDefaultedArrayList(ItemStack.EMPTY, 1);
        this.outputs = newDefaultedArrayList(ItemStack.EMPTY, 1);
        this.recipeOverridingInput = ItemStack.EMPTY;
        this.overriddenRecipes = new HashMap<>();
        this.runningRecipe = null;
    }

    /**
     * @return whether anything meaningful is stored (items, burning state,
     *         progress or experience).
     */
    public boolean hasAny() {
        return this.experience > 0f || !this.inputs.isEmpty() ||
               !this.fuels.isEmpty() || !this.outputs.isEmpty();
    }

    @Override
    public void writeNbt(FureamDataHolder holder, @NotNull NbtCompound nbt) {
        if (this.hasAny()) {
            nbt.put(INPUTS_KEY, stacksToNbt(this.inputs));
            nbt.put(FUELS_KEY, stacksToNbt(this.fuels));
            nbt.put(OUTPUTS_KEY, stacksToNbt(this.outputs));
            NbtCompound tag = new NbtCompound();
            this.recipeOverridingInput.writeNbt(tag);
            nbt.put(RECIPE_OVERRIDING_INPUT_KEY, tag);
            NbtList list = new NbtList();
            for (Map.Entry<KeyableItemStack, Identifier> i :
                 this.overriddenRecipes.entrySet()) {
                ItemStack stack = i.getKey().stack;
                if (!stack.isEmpty()) {
                    NbtCompound sub = new NbtCompound();
                    stack.writeNbt(sub);
                    sub.putString("RecipeId", i.getValue().toString());
                    list.add(sub);
                }
            }
            nbt.put(OVERRIDDEN_RECIPES_KEY, list);
            if (this.runningRecipe != null) {
                nbt.putString(
                    RUNNING_RECIPE_KEY, this.runningRecipe.toString()
                );
            }
            nbt.putFloat(XP_KEY, this.experience);
        }
    }

    @Override
    public void readNbt(FureamDataHolder holder, @NotNull NbtCompound nbt) {
        if (nbt.contains(INPUTS_KEY, NbtElement.LIST_TYPE)) {
            readStacks(nbt.getList(
                INPUTS_KEY, NbtElement.COMPOUND_TYPE
            ), this.inputs);
        }
        if (nbt.contains(FUELS_KEY, NbtElement.LIST_TYPE)) {
            readStacks(nbt.getList(
                FUELS_KEY, NbtElement.COMPOUND_TYPE
            ), this.fuels);
        }
        if (nbt.contains(OUTPUTS_KEY, NbtElement.LIST_TYPE)) {
            readStacks(nbt.getList(
                OUTPUTS_KEY, NbtElement.COMPOUND_TYPE
            ), this.outputs);
        }
        if (nbt.contains(RECIPE_OVERRIDING_INPUT_KEY,
                         NbtElement.COMPOUND_TYPE)) {
            this.recipeOverridingInput =
            ItemStack.fromNbt(nbt.getCompound(RECIPE_OVERRIDING_INPUT_KEY));
        }
        this.overriddenRecipes.clear();
        if (nbt.contains(OVERRIDDEN_RECIPES_KEY, NbtElement.LIST_TYPE)) {
            for (NbtElement i : nbt.getList(OVERRIDDEN_RECIPES_KEY,
                                            NbtElement.COMPOUND_TYPE)) {
                NbtCompound tag = (NbtCompound)i;
                if (tag.contains("RecipeId", NbtElement.STRING_TYPE)) {
                    Identifier identifier =
                    Identifier.tryParse(tag.getString("RecipeId"));
                    if (identifier != null) {
                        ItemStack stack = ItemStack.fromNbt(tag);
                        if (!stack.isEmpty()) {
                            stack.setCount(1);
                            this.overriddenRecipes
                            .put(new KeyableItemStack(stack), identifier);
                        }
                    }
                }
            }
        }
        this.runningRecipe =
        nbt.contains(RUNNING_RECIPE_KEY, NbtElement.STRING_TYPE) ?
        Identifier.tryParse(nbt.getString(RUNNING_RECIPE_KEY)) : null;
        this.experience = nbt.getFloat(XP_KEY);
    }

    public static @NotNull NbtList stacksToNbt(
        @NotNull Iterable<@NotNull ItemStack> stacks
    ) {
        NbtList list = new NbtList();
        int i = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                NbtCompound sub = new NbtCompound();
                stack.writeNbt(sub);
                sub.putInt("Slot", i);
                list.add(sub);
            }
            ++i;
        }
        return list;
    }

    public static void readStacks(
        @NotNull NbtList list, @NotNull List<? super ItemStack> target
    ) {
        if (list.getHeldType() == NbtElement.COMPOUND_TYPE) {
            for (NbtElement element : list) {
                NbtCompound sub = (NbtCompound)element;
                if (sub.contains("Slot", NbtElement.INT_TYPE)) {
                    ItemStack stack = ItemStack.fromNbt(sub);
                    if (!stack.isEmpty()) {
                        target.set(sub.getInt("Slot"), stack);
                    }
                }
            }
        }
    }
}
