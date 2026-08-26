package io.github.eat_ram.fuream.logic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.compat.BlockCompat;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.RecipeCompat;
import io.github.eat_ram.fuream.compat.RecipeHandle;
import io.github.eat_ram.fuream.compat.ServerVersion;
import io.github.eat_ram.fuream.compat.FurnaceCompat;
import io.github.eat_ram.fuream.compat.VersionAdapters;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FureamFurnaceEngine {
    public static void tick(FurnaceContext ctx, FureamWorldConfig config) {
        World world = ctx.getWorld();
        if (world == null) return;
        FureamFurnaceData data = ctx.data;
        int inputCount = slotCount(config.getInputSlotCount().get(ctx.type));
        int fuelCount = slotCount(config.getFuelSlotCount().get(ctx.type));
        int outputCount = slotCount(config.getOutputSlotCount().get(ctx.type));
        ensureSize(data.inputs, inputCount);
        ensureSize(data.fuels, fuelCount);
        ensureSize(data.outputs, outputCount);

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        BlockState state = block.getState();
        if (!(state instanceof org.bukkit.block.Furnace)) return;
        org.bukkit.block.Furnace furnace = (org.bukkit.block.Furnace) state;
        ingestVanillaLane(ctx, furnace.getInventory(), inputCount, fuelCount, outputCount);
        FurnaceCompat.setCookTime(furnace, 0);
        FurnaceCompat.setBurnTime(furnace, 0);

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
                Bukkit.getPluginManager().callEvent(event);
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

        BlockCompat.setLit(block, ctx.burnTime > 0);
        io.github.eat_ram.fuream.hook.FurnaceManager.refreshSessions(ctx);
    }

    private static void ignite(FurnaceContext ctx, Block block, int fuelCount) {
        int fuelSlot = firstFuel(ctx.data.fuels, fuelCount);
        if (fuelSlot < 0) return;
        ItemStack fuel = ctx.data.fuels.get(fuelSlot);
        int fuelTime = FuelTable.getFuelTime(fuel);
        if (ctx.type != FurnaceType.FURNACE) fuelTime = Math.max(1, fuelTime / 2);

        FurnaceBurnEvent event = new FurnaceBurnEvent(block, one(fuel), fuelTime);
        Bukkit.getPluginManager().callEvent(event);
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
        if (!ServerVersion.CURRENT.atLeast(1, 18)) return recipe.cookingTime;
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.inventory.FurnaceStartSmeltEvent");
            for (Constructor<?> constructor : eventClass.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 3 || !types[0].isInstance(block) || !types[1].isInstance(input) ||
                    !types[2].isInstance(recipe.nativeRecipe)) continue;
                Event event = (Event) constructor.newInstance(block, one(input), recipe.nativeRecipe);
                Bukkit.getPluginManager().callEvent(event);
                if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) return Integer.MAX_VALUE;
                Method getter = eventClass.getMethod("getTotalCookTime");
                return ((Number) getter.invoke(event)).intValue();
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return recipe.cookingTime;
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
        int empty = firstEmpty(fuels, maxSlots);
        if (empty < 0) return;
        ItemStack found = fuels.get(bucketSlot);
        found.setAmount(found.getAmount() - 1);
        if (found.getAmount() <= 0) fuels.set(bucketSlot, ItemCompat.empty());
        fuels.set(empty, new ItemStack(waterBucket));
    }

    private static void ingestVanillaLane(
        FurnaceContext ctx, FurnaceInventory inventory, int inputCount, int fuelCount, int outputCount
    ) {
        ItemStack smelting = inventory.getSmelting();
        if (!ItemCompat.isEmpty(smelting)) {
            ItemStack remainder = insertStackIntoList(ctx.data.inputs, smelting, inputCount);
            inventory.setSmelting(ItemCompat.isEmpty(remainder) ? null : remainder);
            ctx.dirty = true;
        }
        ItemStack fuel = inventory.getFuel();
        if (!ItemCompat.isEmpty(fuel)) {
            ItemStack remainder = insertStackIntoList(ctx.data.fuels, fuel, fuelCount);
            inventory.setFuel(ItemCompat.isEmpty(remainder) ? null : remainder);
            ctx.dirty = true;
        }
        ItemStack result = inventory.getResult();
        if (!ItemCompat.isEmpty(result)) {
            inventory.setResult(recoverVanillaOutput(ctx.data.outputs, result, outputCount));
            ctx.dirty = true;
        }
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

    private static int slotCount(Integer configured) {
        return Math.max(1, configured == null ? 9 : configured);
    }

    private static void ensureSize(List<ItemStack> slots, int size) {
        while (slots.size() < size) slots.add(ItemCompat.empty());
    }

    private FureamFurnaceEngine() {
        throw new UnsupportedOperationException();
    }
}
