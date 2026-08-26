package io.github.eat_ram.fuream.logic;

import java.util.List;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FureamFurnaceEngine {
    public static void tick(
        @NotNull FurnaceContext ctx, @Nullable FureamWorldConfig config
    ) {
        FureamFurnaceData data = ctx.data;
        World world = ctx.getWorld();
        if (world == null) return;

        // Ensure virtual lists have at least the configured number of slots
        int inCount = config != null ? config.getInputSlotCount().get(ctx.type) : 18;
        int fuelCount = config != null ? config.getFuelSlotCount().get(ctx.type) : 18;
        int outCount = config != null ? config.getOutputSlotCount().get(ctx.type) : 18;
        while (data.inputs.size() < inCount) data.inputs.add(new ItemStack(Material.AIR));
        while (data.fuels.size() < fuelCount) data.fuels.add(new ItemStack(Material.AIR));
        while (data.outputs.size() < outCount) data.outputs.add(new ItemStack(Material.AIR));

        // Ensure default cookTimeTotal
        int baseCookTime = ctx.type == FurnaceType.FURNACE ? 200 : 100;
        if (ctx.cookTimeTotal <= 0) {
            ctx.cookTimeTotal = baseCookTime;
        }

        // Ingest and feed through vanilla furnace inventory (for seamless hopper integration)
        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        BlockState blockState = block.getState();
        if (blockState instanceof org.bukkit.block.Furnace) {
            org.bukkit.block.Furnace furnaceTile = (org.bukkit.block.Furnace) blockState;
            FurnaceInventory inv = furnaceTile.getInventory();

            // 1. Ingest smelting slot (top hopper input)
            ItemStack smelting = inv.getSmelting();
            if (smelting != null && !smelting.getType().isAir() && smelting.getAmount() > 0) {
                ItemStack remainder = insertStackIntoList(data.inputs, smelting, inCount);
                inv.setSmelting(remainder.getType().isAir() ? null : remainder);
                ctx.dirty = true;
            }

            // 2. Ingest fuel slot (side hopper fuel input)
            ItemStack fuel = inv.getFuel();
            if (fuel != null && !fuel.getType().isAir() && fuel.getAmount() > 0) {
                ItemStack remainder = insertStackIntoList(data.fuels, fuel, fuelCount);
                inv.setFuel(remainder.getType().isAir() ? null : remainder);
                ctx.dirty = true;
            }

            // 3. Recover outputs stranded in the hidden vanilla result slot.
            // Bottom hoppers pull directly from data.outputs in FurnaceManager;
            // proactively filling this slot would hide the first smelted item
            // from the custom GUI until a second item is produced.
            ItemStack result = inv.getResult();
            if (result != null && !result.getType().isAir() && result.getAmount() > 0) {
                inv.setResult(recoverVanillaOutput(data.outputs, result, outCount));
                ctx.dirty = true;
            }

            furnaceTile.setCookTime((short) 0);
            furnaceTile.setBurnTime((short) 0);
        }

        boolean wasBurning = ctx.burnTime > 0;
        if (ctx.burnTime > 0) {
            ctx.burnTime--;
            ctx.dirty = true;
        }

        // Find first valid input stack
        int firstInputIdx = -1;
        ItemStack inputStack = null;
        for (int i = 0; i < data.inputs.size(); i++) {
            ItemStack s = data.inputs.get(i);
            if (!s.getType().isAir() && s.getAmount() > 0) {
                firstInputIdx = i;
                inputStack = s;
                break;
            }
        }

        CookingRecipe<?> matchedRecipe = null;
        if (inputStack != null) {
            matchedRecipe = getRecipeForInput(world, inputStack, ctx);
        }

        boolean canSmelt = false;
        ItemStack recipeResult = null;
        int outSlotIdx = -1;

        if (matchedRecipe != null) {
            NamespacedKey matchedKey = matchedRecipe.getKey();
            if (!matchedKey.equals(data.runningRecipe)) {
                data.runningRecipe = matchedKey;
                ctx.dirty = true;
            }
            recipeResult = matchedRecipe.getResult();
            outSlotIdx = findFittingOutputSlot(data.outputs, recipeResult);
            if (outSlotIdx >= 0) {
                canSmelt = true;
                int cookTime = matchedRecipe.getCookingTime();
                if (cookTime <= 0) {
                    cookTime = (ctx.type == FurnaceType.FURNACE ? 200 : 100);
                }
                ctx.cookTimeTotal = cookTime;
            }
        }

        // Light the furnace if unlit and can smelt
        if (ctx.burnTime <= 0 && canSmelt) {
            int firstFuelIdx = -1;
            ItemStack fuelStack = null;
            for (int i = 0; i < data.fuels.size(); i++) {
                ItemStack s = data.fuels.get(i);
                if (!s.getType().isAir() && s.getAmount() > 0 && FuelTable.isFuel(s)) {
                    firstFuelIdx = i;
                    fuelStack = s;
                    break;
                }
            }

            if (fuelStack != null) {
                int fuelTime = FuelTable.getFuelTime(fuelStack);
                if (ctx.type == FurnaceType.SMOKER || ctx.type == FurnaceType.BLAST_FURNACE) {
                    fuelTime = Math.max(1, fuelTime / 2);
                }

                ctx.burnTime = fuelTime;
                ctx.fuelTimeTotal = fuelTime;
                ctx.dirty = true;

                // Handle recipe remainder (e.g. Lava Bucket -> Bucket in fuel slot)
                if (fuelStack.getType() == Material.LAVA_BUCKET) {
                    data.fuels.set(firstFuelIdx, new ItemStack(Material.BUCKET));
                } else {
                    fuelStack.setAmount(fuelStack.getAmount() - 1);
                    if (fuelStack.getAmount() <= 0) {
                        data.fuels.set(firstFuelIdx, new ItemStack(Material.AIR));
                    }
                }
            }
        }

        // Cook progression
        if (ctx.burnTime > 0 && canSmelt) {
            ctx.cookTime++;
            ctx.dirty = true;

            if (ctx.cookTime >= ctx.cookTimeTotal) {
                ctx.cookTime = 0;

                // Deposit result
                ItemStack currentOut = data.outputs.get(outSlotIdx);
                if (currentOut.getType().isAir()) {
                    data.outputs.set(outSlotIdx, recipeResult.clone());
                } else if (currentOut.isSimilar(recipeResult)) {
                    currentOut.setAmount(currentOut.getAmount() + recipeResult.getAmount());
                    data.outputs.set(outSlotIdx, currentOut);
                }

                // Stash experience
                FureamFurnaceLogic.stashExperience(matchedRecipe, data);

                // Wet sponge drying water bucket logic
                if (inputStack.getType() == Material.WET_SPONGE) {
                    for (int i = 0; i < data.fuels.size(); i++) {
                        ItemStack f = data.fuels.get(i);
                        if (f.getType() == Material.BUCKET) {
                            f.setAmount(f.getAmount() - 1);
                            if (f.getAmount() <= 0) {
                                data.fuels.set(i, new ItemStack(Material.WATER_BUCKET));
                            } else {
                                data.fuels.add(new ItemStack(Material.WATER_BUCKET));
                            }
                            break;
                        }
                    }
                }

                // Decrement input
                inputStack.setAmount(inputStack.getAmount() - 1);
                if (inputStack.getAmount() <= 0) {
                    data.inputs.set(firstInputIdx, new ItemStack(Material.AIR));
                }
                data.runningRecipe = null;
            }
        } else {
            if (matchedRecipe == null && data.runningRecipe != null) {
                data.runningRecipe = null;
                ctx.dirty = true;
            }
            if (ctx.cookTime > 0) {
                ctx.cookTime = Math.max(0, ctx.cookTime - 2);
                ctx.dirty = true;
            }
        }

        // Update block lit state
        boolean isBurning = ctx.burnTime > 0;
        if (wasBurning != isBurning) {
            updateBlockLitState(world, ctx.pos.x, ctx.pos.y, ctx.pos.z, isBurning);
        }

        // Sync with open GUI
        if (ctx.activeGui != null) {
            ctx.activeGui.burnTime = ctx.burnTime;
            ctx.activeGui.fuelTimeTotal = Math.max(1, ctx.fuelTimeTotal);
            ctx.activeGui.cookTime = ctx.cookTime;
            ctx.activeGui.cookTimeTotal = Math.max(1, ctx.cookTimeTotal);
            ctx.activeGui.refreshVisuals();
        }
    }

    public static @NotNull ItemStack insertStackIntoList(
        @NotNull List<ItemStack> list, @NotNull ItemStack incoming, int maxSlots
    ) {
        while (list.size() < maxSlots) {
            list.add(new ItemStack(Material.AIR));
        }

        ItemStack remaining = incoming.clone();

        // 1. Stack into existing slots
        for (int i = 0; i < maxSlots && i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s != null && !s.getType().isAir() && s.isSimilar(remaining)) {
                int space = s.getMaxStackSize() - s.getAmount();
                if (space > 0) {
                    int toAdd = Math.min(space, remaining.getAmount());
                    s.setAmount(s.getAmount() + toAdd);
                    remaining.setAmount(remaining.getAmount() - toAdd);
                    if (remaining.getAmount() <= 0) {
                        return new ItemStack(Material.AIR);
                    }
                }
            }
        }

        // 2. Place into empty slots
        for (int i = 0; i < maxSlots && i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s == null || s.getType().isAir()) {
                list.set(i, remaining.clone());
                return new ItemStack(Material.AIR);
            }
        }

        return remaining;
    }

    static @Nullable ItemStack recoverVanillaOutput(
        @NotNull List<ItemStack> outputs, @Nullable ItemStack vanillaResult,
        int maxSlots
    ) {
        if (vanillaResult == null || vanillaResult.getType().isAir() ||
            vanillaResult.getAmount() <= 0) {
            return null;
        }
        ItemStack remainder = insertStackIntoList(outputs, vanillaResult, maxSlots);
        return remainder.getType().isAir() ? null : remainder;
    }

    private static CookingRecipe<?> getRecipeForInput(
        World world, ItemStack input, FurnaceContext ctx
    ) {
        if (ctx.data.runningRecipe != null) {
            for (CookingRecipe<?> recipe : FureamFurnaceLogic.findAllMatches(world, input, ctx.type)) {
                if (recipe.getKey().equals(ctx.data.runningRecipe)) {
                    return recipe;
                }
            }
        }

        NamespacedKey overridden = ctx.data.overriddenRecipes.get(new KeyableItemStack(input));
        if (overridden != null) {
            List<CookingRecipe<?>> matches = FureamFurnaceLogic.findAllMatches(world, input, ctx.type);
            for (CookingRecipe<?> r : matches) {
                if (r.getKey().equals(overridden)) {
                    return r;
                }
            }
        }
        return FureamFurnaceLogic.findRecipe(world, input, ctx.type).orElse(null);
    }

    private static int findFittingOutputSlot(List<ItemStack> outputs, ItemStack result) {
        for (int i = 0; i < outputs.size(); i++) {
            ItemStack s = outputs.get(i);
            if (s == null || s.getType().isAir()) {
                return i;
            }
            if (s.isSimilar(result) && s.getAmount() + result.getAmount() <= s.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    private static void updateBlockLitState(World world, int x, int y, int z, boolean lit) {
        Block block = world.getBlockAt(x, y, z);
        BlockData data = block.getBlockData();
        if (data instanceof Furnace) {
            Furnace furnaceData = (Furnace) data;
            if (furnaceData.isLit() != lit) {
                furnaceData.setLit(lit);
                block.setBlockData(furnaceData, false);
            }
        }
    }

    private FureamFurnaceEngine() {
        throw new UnsupportedOperationException();
    }
}
