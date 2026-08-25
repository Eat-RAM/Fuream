package io.github.eat_ram.fuream.mixin;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.data.FurnaceFureamDataRegistry;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import io.github.eat_ram.fuream.util.MaskedInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;

/**
 * Backs the virtual furnace on {@link AbstractFurnaceBlockEntity}.
 *
 * <ul>
 *   <li>holds the {@link FureamData} (the interface impl);</li>
 *   <li>persists the virtual state through {@code readNbt}/{@code writeNbt}
 *       under {@code FureamData}.</li>
 * </ul>
 *
 * <p>Deliberately <em>no</em>
 * {@link Inventory}/{@link net.minecraft.inventory.SidedInventory} redirects:
 * the block entity keeps its vanilla 3-slot shape so optimized automation code
 * (lithium, etc.) sees an ordinary furnace. Hopper access goes through
 * {@link io.github.eat_ram.fuream.util.FurnaceForHopperInventory}.</p>
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin
extends LockableContainerBlockEntity implements FureamDataHolder {
    @Shadow
    protected DefaultedList<ItemStack> inventory;

    @Shadow
    protected abstract int getFuelTime(ItemStack fuel);

    @Unique
    private @NotNull HashMap<@NotNull String, @NotNull FureamData> fuream$data;

    private AbstractFurnaceBlockEntityMixin() {
        super(null, null, null);
        throw new AssertionError();
    }

    @Override
    public <T extends FureamData> @Nullable T
    getFureamData(String key, Class<T> clazz) {
        FureamData data = this.fuream$data.get(key);
        if (clazz.isInstance(data)) {
            return clazz.cast(data);
        }
        return null;
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void replaceWithMaskedInventory(CallbackInfo info) {
        this.fuream$data = new HashMap<>();
        for (HashMap.Entry<String, Function<
            ? super AbstractFurnaceBlockEntity, ? extends FureamData
        >> i : FurnaceFureamDataRegistry.REGISTRY.entrySet()) {
            this.fuream$data.put(i.getKey(), i.getValue().apply(
                (AbstractFurnaceBlockEntity)(Object)this
            ));
        }
        FureamFurnaceData data =
        this.getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            this.inventory = new MaskedInventory(
                new MaskedInventory.InventoryViewProfile(
                    data.inputs, false
                ), new MaskedInventory.InventoryViewProfile(
                    data.fuels, stack -> this.getFuelTime(stack) > 0, false
                ), new MaskedInventory.InventoryViewProfile(
                    data.outputs, true
                )
            );
        }
    }

    // Not even skips "return true" because vanilla's is problematic
    @Inject(method = "canAcceptRecipeOutput", at = @At(
        value = "INVOKE_ASSIGN", ordinal = 1,
        target =
        "Lnet/minecraft/util/collection/DefaultedList;get(I)Ljava/lang/Object;"
    ), cancellable = true)
    private static void modifyItemStack(
        DynamicRegistryManager registryManager, @Nullable Recipe<?> recipe,
        DefaultedList<ItemStack> slots, int count,
        CallbackInfoReturnable<Boolean> info
    ) {
        if (slots instanceof MaskedInventory) {
            MaskedInventory.MaskedInventoryDelegate delegate =
            ((MaskedInventory)slots).inventoryDelegate;
            if (delegate.size() > 2) {
                if (recipe == null) {
                    info.setReturnValue(false);
                    return;
                }
                ItemStack recipeOutput =
                recipe.getOutput(registryManager).copy();
                List<ItemStack> outputInventory =
                delegate.getProfile(2).inventory;
                for (ItemStack stack : outputInventory) {
                    if (stack.isEmpty()) {
                        info.setReturnValue(true);
                        return;
                    }
                    if (ItemStack.canCombine(recipeOutput, stack)) {
                        int incr = Math.min(
                            recipeOutput.getCount(),
                            stack.getMaxCount() - stack.getCount()
                        );
                        recipeOutput.decrement(incr);
                    }
                    if (recipeOutput.isEmpty()) {
                        info.setReturnValue(true);
                        return;
                    }
                }
                info.setReturnValue(false);
            }
        }
    }

    // This is totally possible
    @Inject(method = "tick", at = @At(
        value = "INVOKE", ordinal = 2,
        target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"
    ))
    private static void correctRecipeRemainder(
        World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity, CallbackInfo info,
        @Local Item item
    ) {
        FurnaceType type = FureamMain.getFurnaceType(blockEntity);
        if (type != null && world != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    FureamFurnaceData data =
                    ((FureamDataHolder)blockEntity)
                    .getFureamData("fuream", FureamFurnaceData.class);
                    if (data != null) {
                        Item remainder = item.getRecipeRemainder();
                        if (remainder != null) {
                            ItemStack remainderStack =
                            new ItemStack(remainder);
                            boolean found = false;
                            int i = 0;
                            for (ItemStack stack : data.fuels) {
                                if (stack.isEmpty()) {
                                    data.fuels.set(i, remainderStack);
                                    found = true;
                                    break;
                                } else if (ItemStack.canCombine(
                                    remainderStack, stack
                                ) && stack.getCount() < stack.getMaxCount()) {
                                    stack.increment(1);
                                    found = true;
                                    break;
                                }
                                ++i;
                            }
                            if (!found) {
                                Vec3d v3 = Vec3d.ofCenter(pos);
                                ItemScatterer.spawn(
                                    world, v3.x, v3.y, v3.z, remainderStack
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    // Because vanilla's is problematic
    @ModifyExpressionValue(method = "tick", at = @At(
        value = "INVOKE", ordinal = 2,
        target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"
    ))
    private static boolean falsify(
        boolean original, World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity
    ) {
        FurnaceType type = FureamMain.getFurnaceType(blockEntity);
        if (type != null && world != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    return ((FureamDataHolder) blockEntity)
                           .getFureamData("fuream", FureamFurnaceData.class) ==
                           null && original;
                }
            }
        }
        return original;
    }

    @Inject(method = "craftRecipe", at = @At(
        value = "INVOKE_ASSIGN", ordinal = 0,
        target =
        "Lnet/minecraft/util/collection/DefaultedList;get(I)Ljava/lang/Object;"
    ), cancellable = true)
    private static void craftRecipe(
        DynamicRegistryManager registryManager, @Nullable Recipe<?> recipe,
        DefaultedList<ItemStack> slots, int count,
        CallbackInfoReturnable<Boolean> info
    ) {
        if (slots instanceof MaskedInventory) {
            MaskedInventory.MaskedInventoryDelegate delegate =
            ((MaskedInventory)slots).inventoryDelegate;
            if (delegate.size() > 2) {
                if (recipe == null) {
                    info.setReturnValue(false);
                    return;
                }
                ItemStack recipeOutput =
                recipe.getOutput(registryManager).copy();
                List<ItemStack> outputInventory =
                delegate.getProfile(2).inventory;
                boolean success = false;
                int i = 0;
                for (ItemStack stack : outputInventory) {
                    if (stack.isEmpty()) {
                        outputInventory.set(i, recipeOutput);
                        success = true;
                        break;
                    }
                    if (ItemStack.canCombine(recipeOutput, stack)) {
                        int incr = Math.min(
                            recipeOutput.getCount(),
                            stack.getMaxCount() - stack.getCount()
                        );
                        stack.increment(incr);
                        recipeOutput.decrement(incr);
                        success = true;
                    }
                    if (recipeOutput.isEmpty()) {
                        break;
                    }
                    ++i;
                }
                if (success) {
                    ItemStack input = slots.get(0);
                    if (input.isOf(Blocks.WET_SPONGE.asItem())) {
                        List<ItemStack> fuels =
                        delegate.getProfile(1).inventory;
                        ItemStack foundBucket = null;
                        for (ItemStack fuel : fuels) {
                            if (!fuel.isEmpty() && fuel.isOf(Items.BUCKET)) {
                                foundBucket = fuel;
                                fuel.decrement(1);
                                break;
                            }
                        }
                        if (foundBucket != null) {
                            boolean foundEmptySlot = false;
                            i = 0;
                            for (ItemStack fuel : fuels) {
                                if (fuel.isEmpty()) {
                                    fuels.set(
                                        i, new ItemStack(Items.WATER_BUCKET)
                                    );
                                    foundEmptySlot = true;
                                    break;
                                }
                                ++i;
                            }
                            if (!foundEmptySlot) {
                                foundBucket.increment(1);
                            }
                        }
                    }
                    input.decrement(1);
                }
                info.setReturnValue(success);
            }
        }
    }

    @Inject(method = "clear", at = @At("RETURN"))
    private void clearFureamInventory(CallbackInfo info) {
        FureamFurnaceData data =
        this.getFureamData("fuream", FureamFurnaceData.class);
        if (data != null) {
            data.inputs.clear();
            data.fuels.clear();
            data.outputs.clear();
            data.recipeOverridingInput = ItemStack.EMPTY;
            data.runningRecipe = null;
        }
    }

    @SuppressWarnings("unchecked")
    @ModifyExpressionValue(method = "getCookTime", at = @At(
        value = "INVOKE",
        target =
        "Lnet/minecraft/recipe/RecipeManager$MatchGetter;getFirstMatch(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;"
    ))
    private static <C extends Inventory, T extends Recipe<C>> Optional<T>
    modifyRecipe(
        Optional<T> original, World world,
        AbstractFurnaceBlockEntity furnace
    ) {
        FurnaceType type = FureamMain.getFurnaceType(furnace);
        if (type != null && world != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    RecipeType<? extends AbstractCookingRecipe> recipeType;
                    if (type == FurnaceType.SMOKER) {
                        recipeType = RecipeType.SMOKING;
                    } else if (type == FurnaceType.BLAST_FURNACE) {
                        recipeType = RecipeType.BLASTING;
                    } else {
                        recipeType = RecipeType.SMELTING;
                    }
                    FureamFurnaceData data =
                    ((FureamDataHolder)furnace)
                    .getFureamData("fuream",  FureamFurnaceData.class);
                    if (data != null) {
                        RecipeManager recipeManager = world.getRecipeManager();
                        ItemStack toFind = furnace.getStack(0);
                        Inventory toFindInv =
                        FureamFurnaceLogic.singleSlotInventory(toFind);
                        Recipe<?> runningRecipe =
                        data.runningRecipe == null ? null :
                        recipeManager.get(data.runningRecipe).orElse(null);
                        if (runningRecipe instanceof AbstractCookingRecipe &&
                            ((AbstractCookingRecipe)runningRecipe).matches(
                                toFindInv, world
                            )) {
                            return (Optional<T>)Optional.of(runningRecipe);
                        }
                        Identifier overrider = data.overriddenRecipes.get(
                            new KeyableItemStack(toFind.copyWithCount(1))
                        );
                        if (overrider != null) {
                            for (AbstractCookingRecipe recipe :
                                 recipeManager.getAllMatches(
                                recipeType, toFindInv, world
                            )) {
                                if (overrider.equals(recipe.getId())) {
                                    data.runningRecipe = recipe.getId();
                                    return (Optional<T>)Optional.of(recipe);
                                }
                            }
                        }
                        data.runningRecipe =
                        original.map(Recipe::getId).orElse(null);
                    }
                }
            }
        }
        return original;
    }

    @SuppressWarnings("unchecked")
    @ModifyExpressionValue(method = "tick", at = @At(
        value = "INVOKE",
        target =
        "Lnet/minecraft/recipe/RecipeManager$MatchGetter;getFirstMatch(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;"
    ))
    private static <C extends Inventory, T extends Recipe<C>> Optional<T>
    modifyRecipe(
        Optional<T> original, World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity
    ) {
        FurnaceType type = FureamMain.getFurnaceType(blockEntity);
        if (type != null && world != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    RecipeType<? extends AbstractCookingRecipe> recipeType;
                    if (type == FurnaceType.SMOKER) {
                        recipeType = RecipeType.SMOKING;
                    } else if (type == FurnaceType.BLAST_FURNACE) {
                        recipeType = RecipeType.BLASTING;
                    } else {
                        recipeType = RecipeType.SMELTING;
                    }
                    FureamFurnaceData data =
                    ((FureamDataHolder)blockEntity)
                    .getFureamData("fuream",  FureamFurnaceData.class);
                    if (data != null) {
                        RecipeManager recipeManager = world.getRecipeManager();
                        ItemStack toFind = blockEntity.getStack(0);
                        Inventory toFindInv =
                        FureamFurnaceLogic.singleSlotInventory(toFind);
                        Recipe<?> runningRecipe =
                        data.runningRecipe == null ? null :
                        recipeManager.get(data.runningRecipe).orElse(null);
                        if (runningRecipe instanceof AbstractCookingRecipe &&
                            ((AbstractCookingRecipe)runningRecipe).matches(
                                toFindInv, world
                            )) {
                            return (Optional<T>)Optional.of(runningRecipe);
                        }
                        Identifier overrider = data.overriddenRecipes.get(
                            new KeyableItemStack(toFind.copyWithCount(1))
                        );
                        if (overrider != null) {
                            for (AbstractCookingRecipe recipe :
                                 recipeManager.getAllMatches(
                                recipeType, toFindInv, world
                            )) {
                                if (overrider.equals(recipe.getId())) {
                                    data.runningRecipe = recipe.getId();
                                    return (Optional<T>)Optional.of(recipe);
                                }
                            }
                        }
                        data.runningRecipe =
                        original.map(Recipe::getId).orElse(null);
                    }
                }
            }
        }
        return original;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private static void extendInventories(
        World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity, CallbackInfo info
    ) {
        FureamFurnaceData data =
        ((FureamDataHolder)blockEntity)
        .getFureamData("fuream", FureamFurnaceData.class);
        FurnaceType type = FureamMain.getFurnaceType(blockEntity);
        if (type != null && world != null && data != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    int lastI = config.getInputSlotCount().get(type) - 1;
                    data.inputs.set(lastI, data.inputs.get(lastI));
                    lastI = config.getFuelSlotCount().get(type) - 1;
                    data.fuels.set(lastI, data.fuels.get(lastI));
                    lastI = config.getOutputSlotCount().get(type) - 1;
                    data.outputs.set(lastI, data.outputs.get(lastI));
                }
            }
        }
    }

    @Inject(method = "tick", at = @At(
        value = "INVOKE", ordinal = 0, shift = At.Shift.AFTER,
        target =
        "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;setLastRecipe(Lnet/minecraft/recipe/Recipe;)V"
    ))
    private static void gainXp(
        World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity, CallbackInfo info,
        @Local Recipe<?> recipe
    ) {
        FureamFurnaceLogic.stashExperience(world, recipe, blockEntity);
    }

    @Inject(method = "tick", at = @At(
        value = "FIELD", opcode = Opcodes.PUTFIELD, ordinal = 1,
        shift = At.Shift.AFTER,
        target =
        "Lnet/minecraft/block/entity/AbstractFurnaceBlockEntity;cookTime:I"
    ))
    private static void resetRunningRecipe(
        World world, BlockPos pos, BlockState state,
        AbstractFurnaceBlockEntity blockEntity, CallbackInfo info
    ) {
        FureamFurnaceData data =
        ((FureamDataHolder)blockEntity)
        .getFureamData("fuream", FureamFurnaceData.class);
        FurnaceType type = FureamMain.getFurnaceType(blockEntity);
        if (type != null && world != null && data != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    data.runningRecipe = null;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Persistence
    // ------------------------------------------------------------------

    @Inject(method = "readNbt", at = @At("RETURN"))
    private void readFureamNbt(NbtCompound nbt, CallbackInfo info) {
        if (this.getFureamData("fuream", FureamFurnaceData.class) != null) {
            FureamFurnaceData data = new FureamFurnaceData();
            this.fuream$data.put("fuream", data);
            if (nbt.contains(FureamMain.DATA_KEY, NbtElement.COMPOUND_TYPE)) {
                data.readNbt(this, nbt.getCompound(FureamMain.DATA_KEY));
            } else {
                data.inputs.set(0, this.inventory.get(0));
                data.fuels.set(0, this.inventory.get(1));
                data.outputs.set(0, this.inventory.get(2));
            }
            this.inventory = new MaskedInventory(
                new MaskedInventory.InventoryViewProfile(
                    data.inputs, false
                ), new MaskedInventory.InventoryViewProfile(
                    data.fuels, stack -> this.getFuelTime(stack) > 0, false
                ), new MaskedInventory.InventoryViewProfile(
                    data.outputs, true
                )
            );
        }
        if (nbt.contains(FureamMain.DATA_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound nbt2 = nbt.getCompound(FureamMain.DATA_KEY);
            for (HashMap.Entry<String, FureamData> i :
                 this.fuream$data.entrySet()) {
                if (!"fuream".equals(i.getKey())) {
                    i.getValue().readNbt(this, nbt2);
                }
            }
        }
    }

    @Inject(method = "writeNbt", at = @At("RETURN"))
    private void writeFureamNbt(NbtCompound nbt, CallbackInfo info) {
        FurnaceType type = FureamMain.getFurnaceType(this);
        if (type != null) {
            World world = this.getWorld();
            if (world != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null &&
                        config.getEnabledFurnaceTypes().contains(type)) {
                        NbtCompound nbt2 = new NbtCompound();
                        for (FureamData i : this.fuream$data.values()) {
                            i.writeNbt(this, nbt2);
                        }
                        if (!nbt2.isEmpty()) {
                            nbt.put(FureamMain.DATA_KEY, nbt2);
                        }
                    }
                }
            }
        }
    }
}
