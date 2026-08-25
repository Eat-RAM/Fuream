package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.BiFunction;

import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.mixin.AbstractFurnaceBlockEntityAccessor;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * The 54-slot view handed to {@code GenericContainerScreenHandler}.
 *
 * <p>Row layout (see the mod spec):
 * <pre>
 *  0..8   inputs (row 1)
 *  9..17  fuel remaining bar  (row 2)
 * 18..26  fuels               (row 3)
 * 27..35  smelt progress bar  (row 4)
 * 36..44  outputs             (row 5)
 * 45..53  decor, slot 49 = lime XP indicator (row 6)
 * </pre>
 *
 * <p>The bar and decor rows are display-only: their item stacks are fixed
 * (built by {@link #refreshVisuals()}) and every mutation is a no-op, so
 * they can never be taken or overwritten. Real state lives in the
 * {@link FureamFurnaceData} of the furnace block entity.
 */
public class FureamScreenInventory implements Inventory {
    public static final int PLAYER_START = 54;
    public static final int PLAYER_END = 90;
    public static final int COLS = 9;
    public static final ArrayList<@NotNull BiFunction<
        ? super FureamScreenInventory, ? super FureamWorldConfig,
        ? extends @NotNull FureamFunctionalArea
    >> FUNCTIONAL_AREA_REGISTRY = new ArrayList<>();

    public final @NotNull FurnaceType furnaceType; // for GUI only
    private FureamWorldConfig config;
    public final @NotNull ItemStack border;
    public final @NotNull Item fuelLeftItem;
    public final @NotNull Item fuelUsedItem;
    public final @NotNull Item progressDoneItem;
    public final @NotNull Item progressRemainingItem;
    public final @NotNull ArrayList<@NotNull FureamFunctionalArea>
    functionalAreas;
    private @Range(from = 0, to = Integer.MAX_VALUE) int currentFunctionalArea;
    public final @NotNull Item nextFunctionalItem;
    public final @NotNull Item prevPageItem;
    public final @NotNull Item nextPageItem;

    // ------------------------------------------------------------------
    //  Slot range helpers (also used by the screen handler)
    // ------------------------------------------------------------------

    public boolean isInputSlot(int slot) {
        return slot >= 0 && slot < 9 && this.page * 9 + slot < this.inputSlots;
    }

    public boolean isFuelRowSlot(int slot) {
        return slot >= 18 && slot < 27 &&
               this.page * 9 + slot - 18 < this.fuelSlots;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= 36 && slot < 45 &&
               this.page * 9 + slot - 36 < this.outputSlots;
    }

    public boolean isFunctionalAreaSlot(int slot) {
        return slot >= 45 && slot < 51;
    }

    public boolean isBarOrDecorSlot(int slot) {
        return !(this.isInputSlot(slot) || this.isFuelRowSlot(slot) ||
                 this.isOutputSlot(slot) || this.isFunctionalAreaSlot(slot));
    }

    public int getInputSlotStop() {
        int diff = this.inputSlots - (this.page * 9);
        return diff >= 9 ? 9 : (diff <= 0 ? 0 : diff);
    }

    public int getFuelSlotStop() {
        int diff = this.fuelSlots - (this.page * 9);
        return diff >= 9 ? 27 : (diff <= 0 ? 18 : diff + 18);
    }

    public int getOutputSlotStop() {
        int diff = this.outputSlots - (this.page * 9);
        return diff >= 9 ? 45 : (diff <= 0 ? 36 : diff + 36);
    }

    public @Range(from = -1, to = Integer.MAX_VALUE) int getLastPage() {
        return (Math.max(Math.max(
            this.inputSlots, this.outputSlots
        ), this.fuelSlots) - 1) / 9;
    }

    public final @Range(from = 1, to = Integer.MAX_VALUE) int inputSlots;
    public final @Range(from = 1, to = Integer.MAX_VALUE) int fuelSlots;
    public final @Range(from = 1, to = Integer.MAX_VALUE) int outputSlots;
    public final @NotNull AbstractFurnaceBlockEntity furnace;
    public final @NotNull RecipeType<? extends AbstractCookingRecipe>
    recipeType;
    /**
     * Expired after one
     * {@link AbstractFurnaceBlockEntity#readNbt(NbtCompound)}.
     */
    public final @NotNull FureamFurnaceData data;
    public final @NotNull ItemStack @NotNull[] fuelBar = new ItemStack[COLS];
    public final @NotNull ItemStack @NotNull[] progBar = new ItemStack[COLS];
    /** Slot 51. */
    public @NotNull ItemStack nextFunctionalBtn = ItemStack.EMPTY;
    /** Slot 52. */
    public @NotNull ItemStack prevPageBtn = ItemStack.EMPTY;
    /** Slot 53. */
    public @NotNull ItemStack nextPageBtn = ItemStack.EMPTY;
    /** Current page (0-based). */
    private @Range(from = 0, to = Integer.MAX_VALUE) int page;

    public FureamScreenInventory(@NotNull AbstractFurnaceBlockEntity furnace) {
        this.furnace = furnace;
        FureamFurnaceData data =
        ((FureamDataHolder)furnace)
        .getFureamData("fuream", FureamFurnaceData.class);
        this.data = data == null ? new FureamFurnaceData() : data;
        this.functionalAreas = new ArrayList<>();
        int inputSlotCount = 9;
        int fuelSlotCount = 9;
        int outputSlotCount = 9;
        Item borderItem = Items.BLACK_STAINED_GLASS_PANE;
        Item fuelLeftItem = Items.ORANGE_STAINED_GLASS_PANE;
        Item fuelUsedItem = Items.BLACK_STAINED_GLASS_PANE;
        Item progressDoneItem = Items.WHITE_STAINED_GLASS_PANE;
        Item progressRemainingItem = Items.BLACK_STAINED_GLASS_PANE;
        Item nextFunctionalItem = Items.COMMAND_BLOCK;
        Item prevPageItem = Items.ARROW;
        Item nextPageItem = Items.ARROW;
        FurnaceType type = FureamMain.getFurnaceType(furnace);
        if (type != null) {
            World world = furnace.getWorld();
            if (world != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null) {
                        this.config = config;
                        inputSlotCount = config.getInputSlotCount().get(type);
                        fuelSlotCount = config.getFuelSlotCount().get(type);
                        outputSlotCount = config.getOutputSlotCount().get(type);
                        Identifier id = Identifier.tryParse(
                            config.getGuiBorderItemId().get(type)
                        );
                        Optional<Item> optionalItem =
                        Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            borderItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiFuelLeftItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            fuelLeftItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiFuelUsedItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            fuelUsedItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiProgressDoneItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            progressDoneItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiProgressRemainingItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            progressRemainingItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiNextFunctionalAreaItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            nextFunctionalItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiPrevPageItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            prevPageItem = optionalItem.get();
                        }
                        id = Identifier.tryParse(
                            config.getGuiNextPageItemId().get(type)
                        );
                        optionalItem = Registries.ITEM.getOrEmpty(id);
                        if (optionalItem.isPresent()) {
                            nextPageItem = optionalItem.get();
                        }
                    }
                }
            }
        }
        if (type == FurnaceType.SMOKER) {
            this.recipeType = RecipeType.SMOKING;
        } else if (type == FurnaceType.BLAST_FURNACE) {
            this.recipeType = RecipeType.BLASTING;
        } else {
            this.recipeType = RecipeType.SMELTING;
        }
        this.furnaceType = type == null ? FurnaceType.FURNACE : type;
        this.inputSlots = inputSlotCount;
        this.fuelSlots = fuelSlotCount;
        this.outputSlots = outputSlotCount;
        this.border = makeTooltipItem(
            new ItemStack(borderItem, 1),
            this.config == null ? "GUI Border" :
            this.config.getGuiBorderItemTitle().get(this.furnaceType)
        );
        this.fuelLeftItem = fuelLeftItem;
        this.fuelUsedItem = fuelUsedItem;
        this.progressDoneItem = progressDoneItem;
        this.progressRemainingItem = progressRemainingItem;
        this.nextFunctionalItem = nextFunctionalItem;
        this.prevPageItem = prevPageItem;
        this.nextPageItem = nextPageItem;
        this.switchPage(0);
        for (BiFunction<
            ? super FureamScreenInventory, ? super FureamWorldConfig,
            ? extends FureamFunctionalArea
        > i : FUNCTIONAL_AREA_REGISTRY) {
            this.functionalAreas.add(i.apply(this, this.config));
        }
        this.nextFunctionalBtn = this.functionalAreas.size() < 2 ? this.border:
                                 makeTooltipItem(
            new ItemStack(this.nextFunctionalItem, 1),
            this.config == null ? "Next Functional Area" :
            this.config.getGuiNextFunctionalAreaItemTitle()
            .get(this.furnaceType)
        );
        this.refreshVisuals();
    }

    // ------------------------------------------------------------------
    //  Visuals
    // ------------------------------------------------------------------

    /**
     * Rebuilds the bar and decor rows from the current furnace state.
     * Called on open and once per tick per open viewer via
     * {@code FureamScreenHandler.sendContentUpdates}.
     *
     * <p>Fuel bar: {@code round(9 * burnTime / burnTotal)} orange panes on
     * the left, black panes on the right. Progress bar:
     * {@code round(9 * cookTime / 200)} white panes on the left. The XP
     * indicator shows the stored experience through a lore tooltip.
     */
    public void refreshVisuals() {
        AbstractFurnaceBlockEntityAccessor accessor =
        (AbstractFurnaceBlockEntityAccessor)this.furnace;
        int fuelCount = barCount(
            accessor.getBurnTime(),
            accessor.getFuelTime()
        );
        int progressCount = barCount(
            accessor.getCookTime(),
            accessor.getCookTimeTotal()
        );
        for (int i = 0; i < COLS; ++i) {
            this.fuelBar[i] = this.addFuelTooltip(new ItemStack(
                i < fuelCount ? this.fuelLeftItem : this.fuelUsedItem, 1
            ));
            this.progBar[i] = this.addProgressTooltip(new ItemStack(
                i < progressCount ? this.progressDoneItem :
                this.progressRemainingItem, 1
            ));
        }
        for (FureamFunctionalArea i : this.functionalAreas) {
            i.refreshVisuals(this);
        }
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getPage() {
        return this.page;
    }

    public void switchPage(int page) {
        if (page >= 0) {
            this.page = page;
            this.prevPageBtn = makeTooltipItem(new ItemStack(
                this.prevPageItem, 1
            ), this.config.getGuiPrevPageItemTitle().get(this.furnaceType),
            String.format(
                this.config.getGuiPrevPageItemTooltip().get(this.furnaceType),
                page + 1
            ));
            this.nextPageBtn = makeTooltipItem(new ItemStack(
                this.nextPageItem, 1
            ), this.config.getGuiNextPageItemTitle().get(this.furnaceType),
            String.format(
                this.config.getGuiNextPageItemTooltip().get(this.furnaceType),
                page + 1
            ));
        }
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int
    getCurrentFunctionalArea() {
        return this.currentFunctionalArea;
    }

    public void setCurrentFunctionalArea(int currentFunctionalArea) {
        this.currentFunctionalArea =
        currentFunctionalArea >= 0 ? currentFunctionalArea : 0;
    }

    @Contract(pure = true)
    private static @Range(from = 0, to = Integer.MAX_VALUE) int
    barCount(int value, int total) {
        if (total <= 0 || value <= 0) {
            return 0;
        }
        int k = Math.round(9f * value / (float)total);
        return Math.max(0, Math.min(COLS, k));
    }

    @Contract("_ -> param1")
    private @NotNull ItemStack addFuelTooltip(@NotNull ItemStack stack) {
        AbstractFurnaceBlockEntityAccessor accessor =
        (AbstractFurnaceBlockEntityAccessor)this.furnace;
        stack.setCustomName(Text.literal(
            this.config.getGuiFuelItemTitle().get(this.furnaceType)
        ));
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(
            String.format(
                this.config.getGuiFuelItemTooltip().get(this.furnaceType),
                accessor.getBurnTime(), accessor.getFuelTime()
            )
        ))));
        NbtCompound display = stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
        display.put(ItemStack.LORE_KEY, lore);
        return stack;
    }

    @Contract("_ -> param1")
    private @NotNull ItemStack addProgressTooltip(@NotNull ItemStack stack) {
        AbstractFurnaceBlockEntityAccessor accessor =
        (AbstractFurnaceBlockEntityAccessor)this.furnace;
        stack.setCustomName(Text.literal(
            this.config.getGuiProgressItemTitle().get(this.furnaceType)
        ));
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal(
            String.format(
                this.config.getGuiProgressItemTooltip().get(this.furnaceType),
                accessor.getCookTime(), accessor.getCookTimeTotal()
            )
        ))));
        NbtCompound display = stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
        display.put(ItemStack.LORE_KEY, lore);
        return stack;
    }

    @Contract("_, _, _ -> param1")
    public static @NotNull ItemStack makeTooltipItem(
        @NotNull ItemStack stack, @Nullable String title,
        @NotNull String @NotNull... lores
    ) {
        if (title != null) {
            stack.setCustomName(Text.literal(title));
        }
        if (lores.length > 0) {
            NbtList loreList = new NbtList();
            for (String lore : lores) {
                loreList.add(NbtString.of(Text.Serializer.toJson(
                    Text.literal(lore)
                )));
            }
            NbtCompound display =
            stack.getOrCreateSubNbt(ItemStack.DISPLAY_KEY);
            display.put(ItemStack.LORE_KEY, loreList);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    //  Inventory
    // ------------------------------------------------------------------

    @Override
    @Contract(pure = true)
    public int size() {
        return 54;
    }

    @Override
    @Contract(value = "-> false", pure = true)
    public boolean isEmpty() {
        return false;
    }

    @Override
    public @NotNull ItemStack getStack(int slot) {
        if (this.isInputSlot(slot)) {
            return this.data.inputs.get(this.page * 9 + slot);
        }
        if (this.isFuelRowSlot(slot)) {
            return this.data.fuels.get(this.page * 9 + slot - 18);
        }
        if (this.isOutputSlot(slot)) {
            return this.data.outputs.get(this.page * 9 + slot - 36);
        }
        if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            return ca < this.functionalAreas.size() ?
                   this.functionalAreas.get(ca).getGuiStack(this, slot - 45) :
                   this.border;
        }
        if (slot >= 9 && slot < 18) {
            return this.fuelBar[slot - 9];
        }
        if (slot >= 27 && slot < 36) {
            return this.progBar[slot - 27];
        }
        if (slot == 51) {
            return this.nextFunctionalBtn;
        }
        if (slot == 52) {
            return this.prevPageBtn;
        }
        if (slot == 53) {
            return this.nextPageBtn;
        }
        return this.border;
    }

    @Override
    public @NotNull ItemStack removeStack(int slot, int amount) {
        ItemStack stack = this.getStack(slot);
        if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            if (ca >= this.functionalAreas.size() ||
                !this.functionalAreas.get(ca).isTakable(this, slot - 45)) {
                return ItemStack.EMPTY;
            }
            ItemStack split = stack.split(amount);
            this.markDirty();
            return split;
        }
        if (!this.isBarOrDecorSlot(slot) && !stack.isEmpty()) {
            ItemStack split = stack.split(amount);
            this.markDirty();
            return split;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull ItemStack removeStack(int slot) {
        ItemStack stack = this.getStack(slot);
        if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            if (ca >= this.functionalAreas.size() ||
                !this.functionalAreas.get(ca).isTakable(this, slot - 45)) {
                return ItemStack.EMPTY;
            }
            this.setStack(slot, ItemStack.EMPTY);
            return stack;
        }
        if (!this.isBarOrDecorSlot(slot) && !stack.isEmpty()) {
            this.setStack(slot, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStack(int slot, @NotNull ItemStack stack) {
        if (this.isInputSlot(slot)) {
            ItemStack firstInput = ItemStack.EMPTY;
            int firstInputSlot = 0;
            for (; firstInputSlot < this.inputSlots; ++firstInputSlot) {
                ItemStack input = this.data.inputs.get(firstInputSlot);
                if (!input.isEmpty()) {
                    firstInput = input;
                    break;
                }
            }
            boolean bl = this.page * 9 + slot > firstInputSlot || (
                !firstInput.isEmpty() &&
                ItemStack.canCombine(firstInput, stack)
            );
            this.data.inputs.set(this.page * 9 + slot, stack);
            if (!bl) {
                this.data.runningRecipe = null;
                AbstractFurnaceBlockEntityAccessor accessor =
                (AbstractFurnaceBlockEntityAccessor)this.furnace;
                accessor.setCookTimeTotal(
                    AbstractFurnaceBlockEntityAccessor.invokeGetCookTime(
                        this.furnace.getWorld(), this.furnace
                    )
                );
                accessor.setCookTime(0);
            }
            this.markDirty();
        } else if (this.isFuelRowSlot(slot)) {
            this.data.fuels.set(this.page * 9 + slot - 18, stack);
            this.markDirty();
        } else if (this.isOutputSlot(slot)) {
            this.data.outputs.set(this.page * 9 + slot - 36, stack);
            this.markDirty();
        } else if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            if (ca < this.functionalAreas.size()) {
                FureamFunctionalArea area = this.functionalAreas.get(ca);
                if (area.isStorable(this, slot - 45)) {
                    area.setGuiStack(this, slot - 45, stack);
                }
            }
        }
        // Bar and decor rows are immutable.
    }

    @Override
    public void markDirty() {
        this.furnace.markDirty();
    }

    @Override
    public void clear() {
        this.data.inputs.clear();
        this.data.fuels.clear();
        this.data.outputs.clear();
        this.data.recipeOverridingInput = ItemStack.EMPTY;
    }

    @Override
    public boolean canPlayerUse(@NotNull PlayerEntity player) {
        return this.furnaceSquaredDistance(player) <= 64.;
    }

    @Override
    public boolean isValid(int slot, @NotNull ItemStack stack) {
        if (!stack.isEmpty()) {
            if (this.isInputSlot(slot)) {
                return true;
            }
            if (this.isFuelRowSlot(slot)) {
                return FureamFurnaceLogic.isFuel(stack);
            }
        }
        return false; // bars, outputs, recipe setting slot and decor are never insertion targets
    }

    private double furnaceSquaredDistance(@NotNull PlayerEntity player) {
        return player.squaredDistanceTo(
            this.furnace.getPos().getX() + 0.5,
            this.furnace.getPos().getY() + 0.5,
            this.furnace.getPos().getZ() + 0.5
        );
    }

    static {
        FUNCTIONAL_AREA_REGISTRY.add(RecipeSettingAndXpFunctionalArea::new);
    }
}
