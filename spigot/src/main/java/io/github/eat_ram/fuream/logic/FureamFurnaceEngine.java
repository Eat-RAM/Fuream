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

        // Always compute cookTimeTotal according to current rate factor
        int baseCookTime = ctx.type == FurnaceType.FURNACE ? 200 : 100;
        double rateUpgradeFactor = ctx.getRateUpgradeFactor();
        ctx.cookTimeTotal = Math.max(1, (int) Math.ceil(baseCookTime / rateUpgradeFactor));

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

            // 3. Feed outputs into result slot (bottom hopper output extraction)
            ItemStack result = inv.getResult();
            if (result == null || result.getType().isAir()) {
                ItemStack nextOut = extractFirstNonEmpty(data.outputs);
                if (nextOut != null) {
                    inv.setResult(nextOut);
                    ctx.dirty = true;
                }
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
            recipeResult = matchedRecipe.getResult();
            outSlotIdx = findFittingOutputSlot(data.outputs, recipeResult);
            if (outSlotIdx >= 0) {
                canSmelt = true;
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
                int baseFuelTime = FuelTable.getFuelTime(fuelStack);
                double fuelUpgradeFactor = ctx.getFuelUpgradeFactor();
                int fuelTime = (int) Math.round(baseFuelTime * fuelUpgradeFactor);

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

                // Deduct fuel time compensation so that speed upgrade does not artificially multiply fuel capacity
                int extraBurnTimeCost = baseCookTime - ctx.cookTimeTotal;
                if (extraBurnTimeCost > 0) {
                    ctx.burnTime -= extraBurnTimeCost;
                    while (ctx.burnTime <= 0) {
                        int nextFuelIdx = -1;
                        ItemStack nextFuelStack = null;
                        for (int i = 0; i < data.fuels.size(); i++) {
                            ItemStack s = data.fuels.get(i);
                            if (!s.getType().isAir() && s.getAmount() > 0 && FuelTable.isFuel(s)) {
                                nextFuelIdx = i;
                                nextFuelStack = s;
                                break;
                            }
                        }
                        if (nextFuelStack == null) {
                            break;
                        }
                        int nextBaseFuelTime = FuelTable.getFuelTime(nextFuelStack);
                        double nextFuelFactor = ctx.getFuelUpgradeFactor();
                        int nextFuelTime = (int) Math.round(nextBaseFuelTime * nextFuelFactor);
                        if (nextFuelTime <= 0) {
                            break;
                        }
                        ctx.burnTime += nextFuelTime;
                        ctx.fuelTimeTotal = nextFuelTime;
                        if (nextFuelStack.getType() == Material.LAVA_BUCKET) {
                            data.fuels.set(nextFuelIdx, new ItemStack(Material.BUCKET));
                        } else {
                            nextFuelStack.setAmount(nextFuelStack.getAmount() - 1);
                            if (nextFuelStack.getAmount() <= 0) {
                                data.fuels.set(nextFuelIdx, new ItemStack(Material.AIR));
                            }
                        }
                    }
                    if (ctx.burnTime < 0) {
                        ctx.burnTime = 0;
                    }
                }
            }
        } else if (ctx.cookTime > 0) {
            ctx.cookTime = Math.max(0, ctx.cookTime - 2);
            ctx.dirty = true;
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

    public static @Nullable ItemStack extractFirstNonEmpty(@NotNull List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s != null && !s.getType().isAir() && s.getAmount() > 0) {
                ItemStack single = s.clone();
                single.setAmount(1);
                s.setAmount(s.getAmount() - 1);
                if (s.getAmount() <= 0) {
                    list.set(i, new ItemStack(Material.AIR));
                }
                return single;
            }
        }
        return null;
    }

    private static CookingRecipe<?> getRecipeForInput(
        World world, ItemStack input, FurnaceContext ctx
    ) {
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
