package io.github.eat_ram.fuream.util;

import java.util.List;

import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.mixin.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

public class FurnaceForHopperInventory implements SidedInventory {
    public final @NotNull AbstractFurnaceBlockEntity furnace;
    private final @Range(from = 0, to = Integer.MAX_VALUE) int @NotNull[]
    inputSlots;
    private final @Range(from = 0, to = Integer.MAX_VALUE) int @NotNull[]
    fuelSlots;
    private final @Range(from = 0, to = Integer.MAX_VALUE) int @NotNull[]
    fuelAndOutputSlots;
    public final RecipeType<? extends AbstractCookingRecipe> recipeType;
    public final boolean preventsInsertNonSmeltable;

    public
    FurnaceForHopperInventory(@NotNull AbstractFurnaceBlockEntity furnace) {
        this.furnace = furnace;
        int inputSlotCount = 9;
        int fuelSlotCount = 9;
        int outputSlotCount = 9;
        RecipeType<? extends AbstractCookingRecipe> recipeType =
        RecipeType.SMELTING;
        boolean preventsInsertNonSmeltable = false;
        FurnaceType type = FureamMain.getFurnaceType(furnace);
        if (type != null) {
            World world = furnace.getWorld();
            if (world != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null) {
                        inputSlotCount = config.getInputSlotCount().get(type);
                        fuelSlotCount = config.getFuelSlotCount().get(type);
                        outputSlotCount = config.getOutputSlotCount().get(type);
                        if (type == FurnaceType.SMOKER) {
                            recipeType = RecipeType.SMOKING;
                        } else if (type == FurnaceType.BLAST_FURNACE) {
                            recipeType = RecipeType.BLASTING;
                        }
                        preventsInsertNonSmeltable =
                        config.getPreventsHopperInsertNonSmeltable()
                        .contains(type);
                    }
                }
            }
        }
        int i = 0;
        this.inputSlots = new int[inputSlotCount];
        for (int k = 0; k < inputSlotCount; ++k) {
            this.inputSlots[k] = i;
            ++i;
        }
        this.fuelSlots = new int[fuelSlotCount];
        this.fuelAndOutputSlots = new int[fuelSlotCount + outputSlotCount];
        for (int k = 0; k < fuelSlotCount; ++k) {
            this.fuelSlots[k] = i;
            this.fuelAndOutputSlots[k] = i;
            ++i;
        }
        for (int k = fuelSlotCount; k < this.fuelAndOutputSlots.length; ++k) {
            this.fuelAndOutputSlots[k] = i;
            ++i;
        }
        this.recipeType = recipeType;
        this.preventsInsertNonSmeltable = preventsInsertNonSmeltable;
    }

    @Override
    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int @NotNull[]
    getAvailableSlots(Direction side) {
        if (side == Direction.DOWN) {
            return this.fuelAndOutputSlots;
        }
        if (side == Direction.UP) {
            return this.inputSlots;
        }
        return this.fuelSlots;
    }

    @Override
    public boolean
    canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        return this.isValid(slot, stack);
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        if (slot >= this.inputSlots.length + this.fuelSlots.length) {
            return false;
        }
        if (slot >= this.inputSlots.length) {
            ItemStack itemStack = this.getStack(slot);
            return AbstractFurnaceBlockEntity.canUseAsFuel(stack) ||
                   stack.isOf(Items.BUCKET) && !itemStack.isOf(Items.BUCKET);
        }
        return !this.preventsInsertNonSmeltable ||
               FureamFurnaceLogic.isAcceptableInput(
                   this.furnace.getWorld(), stack, this.recipeType
               );
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        if (dir == Direction.DOWN && slot >= this.inputSlots.length &&
            slot < this.inputSlots.length + this.fuelSlots.length) {
            return stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.BUCKET);
        }
        return true;
    }

    @Override
    @Contract(pure = true)
    public int size() {
        return this.inputSlots.length + this.fuelAndOutputSlots.length;
    }

    @Override
    public boolean isEmpty() {
        FureamFurnaceData data =
        ((FureamDataHolder)this.furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            for (int i = 0; i < this.inputSlots.length; ++i) {
                if (!data.inputs.get(i).isEmpty()) {
                    return false;
                }
            }
            for (int i = 0; i < this.fuelSlots.length; ++i) {
                if (!data.fuels.get(i).isEmpty()) {
                    return false;
                }
            }
            for (int i = 0;
                 i < this.fuelAndOutputSlots.length - this.fuelSlots.length;
                 ++i) {
                if (!data.outputs.get(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }
        return this.furnace.isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        FureamFurnaceData data =
        ((FureamDataHolder)this.furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            if (slot < this.inputSlots.length) {
                return data.inputs.get(slot);
            }
            slot -= this.inputSlots.length;
            if (slot < this.fuelSlots.length) {
                return data.fuels.get(slot);
            }
            slot -= this.fuelSlots.length;
            return data.outputs.get(slot);
        }
        return this.furnace.getStack(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        FureamFurnaceData data =
        ((FureamDataHolder)this.furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            if (slot < this.inputSlots.length) {
                return Inventories.splitStack(data.inputs, slot, amount);
            }
            slot -= this.inputSlots.length;
            if (slot < this.fuelSlots.length) {
                return Inventories.splitStack(data.fuels, slot, amount);
            }
            slot -= this.fuelSlots.length;
            return Inventories.splitStack(data.outputs, slot, amount);
        }
        return this.furnace.removeStack(slot, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        FureamFurnaceData data =
        ((FureamDataHolder)this.furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            if (slot < this.inputSlots.length) {
                return Inventories.removeStack(data.inputs, slot);
            }
            slot -= this.inputSlots.length;
            if (slot < this.fuelSlots.length) {
                return Inventories.removeStack(data.fuels, slot);
            }
            slot -= this.fuelSlots.length;
            return Inventories.removeStack(data.outputs, slot);
        }
        return this.furnace.removeStack(slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        FureamFurnaceData data =
        ((FureamDataHolder)this.furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            List<ItemStack> opList;
            int opSlot = slot;
            if (opSlot < this.inputSlots.length) {
                opList = data.inputs;
            } else {
                opSlot -= this.inputSlots.length;
                if (opSlot < this.fuelSlots.length) {
                    opList = data.fuels;
                } else {
                    opSlot -= this.fuelSlots.length;
                    opList = data.outputs;
                }
            }
            ItemStack firstInput = ItemStack.EMPTY;
            int firstInputSlot = 0;
            for (; firstInputSlot < this.inputSlots.length; ++firstInputSlot) {
                ItemStack input = data.inputs.get(firstInputSlot);
                if (!input.isEmpty()) {
                    firstInput = input;
                    break;
                }
            }
            boolean bl = slot > firstInputSlot || (
                !firstInput.isEmpty() &&
                ItemStack.canCombine(firstInput, stack)
            );
            opList.set(opSlot, stack);
            if (stack.getCount() > this.furnace.getMaxCountPerStack()) {
                stack.setCount(this.furnace.getMaxCountPerStack());
            }
            if (!bl) {
                data.runningRecipe = null;
                AbstractFurnaceBlockEntityAccessor accessor =
                (AbstractFurnaceBlockEntityAccessor)this.furnace;
                accessor.setCookTimeTotal(
                    AbstractFurnaceBlockEntityAccessor.invokeGetCookTime(
                        this.furnace.getWorld(), this.furnace
                    )
                );
                accessor.setCookTime(0);
                this.furnace.markDirty();
            }
        }
    }

    @Override
    public void markDirty() {
        this.furnace.markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return this.furnace.canPlayerUse(player);
    }

    @Override
    public void clear() {
        this.furnace.clear();
    }

    @Override
    public int getMaxCountPerStack() {
        return this.furnace.getMaxCountPerStack();
    }
}
