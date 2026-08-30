package io.github.eat_ram.fuream.logic;

import java.util.List;

import io.github.eat_ram.fuream.ResolvedFurnaceConfig;
import io.github.eat_ram.fuream.compat.FurnaceEventDispatcher;
import io.github.eat_ram.fuream.compat.FurnaceEventCompat;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.RecipeCompat;
import io.github.eat_ram.fuream.compat.RecipeHandle;
import io.github.eat_ram.fuream.compat.VersionAdapters;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FureamFurnaceEngine {
    public static void tick(FurnaceContext ctx, ResolvedFurnaceConfig config) {
        World world = ctx.getWorld();
        if (world == null) return;
        FureamFurnaceData data = ctx.data;
        int inputCount = config.inputSlots;
        int fuelCount = config.fuelSlots;
        int outputCount = config.outputSlots;
        ensureSize(data.inputs, inputCount);
        ensureSize(data.fuels, fuelCount);
        ensureSize(data.outputs, outputCount);

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        if (ctx.burnTime > 0) {
            ctx.burnTime--;
            ctx.dirty = true;
        }

        int inputSlot = firstPresent(data.inputs);
        ItemStack input = inputSlot < 0 ? null : data.inputs.get(inputSlot);
        updateInputIdentity(ctx, inputSlot, input);

        RecipeHandle recipe = input == null ? null : getRecipeForInput(input, ctx);
        ItemStack result = recipe == null ? null : recipe.result.clone();
        boolean canSmelt = recipe != null && canFitAll(data.outputs, result, outputCount);
        if (recipe != null) {
            if (!recipe.id.equals(data.runningRecipe)) {
                ctx.cookTime = 0;
                ctx.cookTimeTotal = Math.max(1, recipe.cookingTime);
                ctx.startSmeltPending = true;
            }
            data.runningRecipe = recipe.id;
        } else {
            data.runningRecipe = null;
        }

        if (ctx.burnTime <= 0 && canSmelt) {
            ignite(ctx, block, fuelCount);
        }

        if (ctx.burnTime > 0 && canSmelt) {
            if (ctx.startSmeltPending) {
                int requestedTime = fireStartSmelt(block, input, recipe);
                ctx.cookTimeTotal = Math.max(1, requestedTime);
                ctx.startSmeltPending = false;
                ctx.dirty = true;
            }
            ctx.cookTime++;
            ctx.dirty = true;
            if (ctx.cookTime >= Math.max(1, ctx.cookTimeTotal)) {
                FurnaceSmeltEvent event = new FurnaceSmeltEvent(block, one(input), result.clone());
                FurnaceEventDispatcher.call(event);
                ItemStack eventResult = event.getResult();
                if (event.isCancelled() || ItemCompat.isEmpty(eventResult) ||
                    !canFitAll(data.outputs, eventResult, outputCount)) {
                    ctx.cookTime = Math.max(0, ctx.cookTimeTotal - 1);
                } else {
                    ItemStack remainder = insertStackIntoList(data.outputs, eventResult, outputCount);
                    if (ItemCompat.isEmpty(remainder)) {
                        FureamFurnaceLogic.stashExperience(recipe, data);
                        handleWetSponge(input, data.fuels, fuelCount);
                        decrement(data.inputs, inputSlot);
                        ctx.cookTime = 0;
                        data.runningRecipe = null;
                        ctx.lastInputKey = null;
                        ctx.lastInputSlot = -1;
                        ctx.startSmeltPending = true;
                    }
                }
            }
        } else if (!canSmelt && ctx.cookTime > 0) {
            ctx.cookTime = Math.max(0, ctx.cookTime - 2);
            ctx.dirty = true;
        }

    }

    private static void ignite(FurnaceContext ctx, Block block, int fuelCount) {
        int fuelSlot = firstFuel(ctx.data.fuels, fuelCount);
        if (fuelSlot < 0) return;
        ItemStack fuel = ctx.data.fuels.get(fuelSlot);
        int fuelTime = effectiveFuelTime(FuelTable.getFuelTime(fuel), ctx.type);

        FurnaceBurnEvent event = new FurnaceBurnEvent(block, one(fuel), fuelTime);
        FurnaceEventDispatcher.call(event);
        if (event.isCancelled() || !event.isBurning() || event.getBurnTime() <= 0) return;

        ctx.burnTime = event.getBurnTime();
        ctx.fuelTimeTotal = event.getBurnTime();
        Material lavaBucket = Material.matchMaterial("LAVA_BUCKET");
        Material bucket = Material.matchMaterial("BUCKET");
        if (lavaBucket != null && bucket != null && fuel.getType() == lavaBucket) {
            ctx.data.fuels.set(fuelSlot, new ItemStack(bucket));
        } else {
            decrement(ctx.data.fuels, fuelSlot);
        }
        ctx.dirty = true;
    }

    private static int fireStartSmelt(Block block, ItemStack input, RecipeHandle recipe) {
        return FurnaceEventCompat.fireStartSmelt(
            block, one(input), recipe.nativeRecipe, recipe.cookingTime
        );
    }

    private static RecipeHandle getRecipeForInput(ItemStack input, FurnaceContext ctx) {
        if (ctx.data.runningRecipe != null) {
            RecipeHandle running = RecipeCompat.findById(input, ctx.type, ctx.data.runningRecipe);
            if (running != null) return running;
        }
        if (VersionAdapters.current().supportsRecipeOverrides()) {
            String overridden = ctx.data.overriddenRecipes.get(new KeyableItemStack(one(input)));
            RecipeHandle selected = RecipeCompat.findById(input, ctx.type, overridden);
            if (selected != null) return selected;
        }
        return RecipeCompat.findFirst(input, ctx.type);
    }

    static void handleWetSponge(ItemStack input, List<ItemStack> fuels, int maxSlots) {
        Material wetSponge = Material.matchMaterial("WET_SPONGE");
        Material bucket = Material.matchMaterial("BUCKET");
        Material waterBucket = Material.matchMaterial("WATER_BUCKET");
        if (wetSponge == null || bucket == null || waterBucket == null || input.getType() != wetSponge) return;

        int bucketSlot = -1;
        for (int i = 0; i < Math.min(maxSlots, fuels.size()); i++) {
            if (!ItemCompat.isEmpty(fuels.get(i)) && fuels.get(i).getType() == bucket) {
                bucketSlot = i;
                break;
            }
        }
        if (bucketSlot < 0) return;
        ItemStack found = fuels.get(bucketSlot);
        found.setAmount(found.getAmount() - 1);
        if (found.getAmount() <= 0) {
            fuels.set(bucketSlot, new ItemStack(waterBucket));
            return;
        }
        int empty = firstEmpty(fuels, maxSlots);
        if (empty < 0) {
            found.setAmount(found.getAmount() + 1);
            return;
        }
        fuels.set(empty, new ItemStack(waterBucket));
    }

    static int effectiveFuelTime(int vanillaFuelTime, io.github.eat_ram.fuream.api.FurnaceType type) {
        return Math.max(0, vanillaFuelTime);
    }

    public static ItemStack insertStackIntoList(List<ItemStack> slots, ItemStack incoming, int maxSlots) {
        if (ItemCompat.isEmpty(incoming)) return ItemCompat.empty();
        ensureSize(slots, maxSlots);
        ItemStack remaining = incoming.clone();
        for (int i = 0; i < maxSlots && !ItemCompat.isEmpty(remaining); i++) {
            ItemStack current = slots.get(i);
            if (!ItemCompat.isEmpty(current) && current.isSimilar(remaining)) {
                int move = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
                if (move > 0) {
                    current.setAmount(current.getAmount() + move);
                    remaining.setAmount(remaining.getAmount() - move);
                }
            }
        }
        for (int i = 0; i < maxSlots && !ItemCompat.isEmpty(remaining); i++) {
            if (ItemCompat.isEmpty(slots.get(i))) {
                int move = Math.min(remaining.getAmount(), remaining.getMaxStackSize());
                ItemStack placed = remaining.clone();
                placed.setAmount(move);
                slots.set(i, placed);
                remaining.setAmount(remaining.getAmount() - move);
            }
        }
        return ItemCompat.isEmpty(remaining) ? ItemCompat.empty() : remaining;
    }

    public static ItemStack recoverVanillaOutput(
        List<ItemStack> outputs, ItemStack vanillaResult, int maxSlots
    ) {
        if (ItemCompat.isEmpty(vanillaResult)) return null;
        ItemStack remainder = insertStackIntoList(outputs, vanillaResult, Math.max(1, maxSlots));
        return ItemCompat.isEmpty(remainder) ? null : remainder;
    }

    private static boolean canFitAll(List<ItemStack> slots, ItemStack stack, int maxSlots) {
        if (ItemCompat.isEmpty(stack)) return false;
        int capacity = 0;
        ensureSize(slots, maxSlots);
        for (int i = 0; i < maxSlots; i++) {
            ItemStack current = slots.get(i);
            if (ItemCompat.isEmpty(current)) capacity += stack.getMaxStackSize();
            else if (current.isSimilar(stack)) capacity += current.getMaxStackSize() - current.getAmount();
            if (capacity >= stack.getAmount()) return true;
        }
        return false;
    }

    private static int firstFuel(List<ItemStack> fuels, int maxSlots) {
        for (int i = 0; i < Math.min(maxSlots, fuels.size()); i++) {
            if (!ItemCompat.isEmpty(fuels.get(i)) && FuelTable.isFuel(fuels.get(i))) return i;
        }
        return -1;
    }

    private static int firstPresent(List<ItemStack> stacks) {
        for (int i = 0; i < stacks.size(); i++) if (!ItemCompat.isEmpty(stacks.get(i))) return i;
        return -1;
    }

    private static int firstEmpty(List<ItemStack> stacks, int maxSlots) {
        ensureSize(stacks, maxSlots);
        for (int i = 0; i < maxSlots; i++) if (ItemCompat.isEmpty(stacks.get(i))) return i;
        return -1;
    }

    private static void decrement(List<ItemStack> slots, int slot) {
        ItemStack stack = slots.get(slot);
        stack.setAmount(stack.getAmount() - 1);
        if (stack.getAmount() <= 0) slots.set(slot, ItemCompat.empty());
    }

    private static ItemStack one(ItemStack stack) {
        ItemStack result = stack.clone();
        result.setAmount(1);
        return result;
    }

    private static boolean sameKey(KeyableItemStack left, KeyableItemStack right) {
        return left == right || left != null && left.equals(right);
    }

    static boolean updateInputIdentity(
        FurnaceContext ctx, int inputSlot, @Nullable ItemStack input
    ) {
        KeyableItemStack inputKey = ItemCompat.isEmpty(input)
            ? null : new KeyableItemStack(one(input));
        if (inputSlot == ctx.lastInputSlot && sameKey(ctx.lastInputKey, inputKey)) return false;
        ctx.lastInputSlot = inputSlot;
        ctx.lastInputKey = inputKey;
        ctx.cookTime = 0;
        ctx.data.runningRecipe = null;
        ctx.startSmeltPending = true;
        ctx.dirty = true;
        return true;
    }

    private static void ensureSize(List<ItemStack> slots, int size) {
        while (slots.size() < size) slots.add(ItemCompat.empty());
    }

    private FureamFurnaceEngine() {
    }
}
