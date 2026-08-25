package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import static io.github.eat_ram.fuream.screen.FureamScreenInventory
              .makeTooltipItem;

public class RecipeSettingAndXpFunctionalArea implements FureamFunctionalArea {
    private FureamWorldConfig config;
    public final @NotNull Item prevRecipeItem;
    public final @NotNull Item nextRecipeItem;
    public final @NotNull Item xpIndicatorItem;
    /** Slot 1 (46 in {@link FureamScreenInventory}. */
    public @NotNull ItemStack prevRecipeBtn = ItemStack.EMPTY;
    /** Slot 2 (47 in {@link FureamScreenInventory}. */
    public @NotNull ItemStack nextRecipeBtn = ItemStack.EMPTY;
    /** Slot 3 (48 in {@link FureamScreenInventory}. */
    public @NotNull ItemStack recipeResultPreview = ItemStack.EMPTY;
    /** Slot 4 (49 in {@link FureamScreenInventory}. */
    public @NotNull ItemStack xpIndicator = ItemStack.EMPTY;
    public final @NotNull ArrayList<@NotNull AbstractCookingRecipe>
    currentMatchingRecipes = new ArrayList<>();
    /** Selected recipe against {@link #currentMatchingRecipes} (1-based). 0
     *  for default arbitrary. */
    private @Range(from = 0, to = Integer.MAX_VALUE) int selectedRecipeIndex;
    private float cachedXp;

    public RecipeSettingAndXpFunctionalArea(
        FureamScreenInventory inv, FureamWorldConfig config
    ) {
        this.config = config;
        Item prevRecipeItem = Items.BOOK;
        Item nextRecipeItem = Items.BOOK;
        Item xpIndicatorItem = Items.LIME_STAINED_GLASS_PANE;
        Identifier id = Identifier.tryParse(
            config.getGuiPrevRecipeItemId().get(inv.furnaceType)
        );
        Optional<Item> optionalItem = Registries.ITEM.getOrEmpty(id);
        if (optionalItem.isPresent()) {
            prevRecipeItem = optionalItem.get();
        }
        id = Identifier.tryParse(
            config.getGuiNextRecipeItemId().get(inv.furnaceType)
        );
        optionalItem = Registries.ITEM.getOrEmpty(id);
        if (optionalItem.isPresent()) {
            nextRecipeItem = optionalItem.get();
        }
        id = Identifier.tryParse(
            config.getGuiXpIndicatorItemId().get(inv.furnaceType)
        );
        optionalItem = Registries.ITEM.getOrEmpty(id);
        if (optionalItem.isPresent()) {
            xpIndicatorItem = optionalItem.get();
        }
        this.prevRecipeItem = prevRecipeItem;
        this.nextRecipeItem = nextRecipeItem;
        this.xpIndicatorItem = xpIndicatorItem;
        this.updateRecipeSettingArea(inv);
    }

    @Override
    @Contract(pure = true)
    public @NotNull ItemStack
    getGuiStack(FureamScreenInventory inv, int slot) {
        switch (slot) {
            case 0: return inv.data.recipeOverridingInput;
            case 1: return this.prevRecipeBtn;
            case 2: return this.nextRecipeBtn;
            case 3: return this.recipeResultPreview;
            case 4: return this.xpIndicator;
            default: return inv.border;
        }
    }

    @Override
    public void
    setGuiStack(FureamScreenInventory inv, int slot, ItemStack newStack) {
        if (slot == 0) {
            inv.data.recipeOverridingInput = newStack;
        }
    }

    @Override
    public void onGuiClick(
        SlotClick slotClick, FureamScreenInventory display, int slot,
        int button, SlotActionType actionType, PlayerEntity player
    ) {
        switch (slot) {
            case 0:
                slotClick.onSlotClick(45, button, actionType, player);
                this.updateRecipeSettingArea(display);
                break;
            case 1: {
                int curRecipeIndex = this.getSelectedRecipeIndex();
                this.switchSelectedRecipeIndex(
                    display, curRecipeIndex > 0 ? curRecipeIndex - 1 :
                             this.currentMatchingRecipes.size()
                );
                break;
            }
            case 2: {
                int curRecipeIndex = this.getSelectedRecipeIndex();
                this.switchSelectedRecipeIndex(
                    display,
                    curRecipeIndex < this.currentMatchingRecipes.size() ?
                    curRecipeIndex + 1 : 0
                );
                break;
            }
            case 4:
                FureamFurnaceLogic.grantExperience(player, display.furnace);
                break;
        }
    }

    @Override
    @Contract(pure = true)
    public boolean isStorable(FureamScreenInventory inv, int slot) {
        return slot == 0;
    }

    @Override
    @Contract(pure = true)
    public boolean isTakable(FureamScreenInventory inv, int slot) {
        return slot == 0;
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int
    getSelectedRecipeIndex() {
        return this.selectedRecipeIndex;
    }

    public void
    switchSelectedRecipeIndex(FureamScreenInventory inv, int recipeIndex) {
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (toFind.isEmpty()) {
            this.selectedRecipeIndex = 0;
            this.prevRecipeBtn = makeTooltipItem(new ItemStack(
                this.prevRecipeItem, 1
            ), this.config.getGuiPrevRecipeItemTitle().get(inv.furnaceType),
            this.config.getGuiPrevRecipeEmptyItemTooltip()
            .get(inv.furnaceType));
            this.nextRecipeBtn = makeTooltipItem(new ItemStack(
                this.nextRecipeItem, 1
            ), this.config.getGuiNextRecipeItemTitle().get(inv.furnaceType),
            this.config.getGuiNextRecipeEmptyItemTooltip()
            .get(inv.furnaceType));
            this.recipeResultPreview = ItemStack.EMPTY;
            return;
        }
        int size = this.currentMatchingRecipes.size();
        World world = inv.furnace.getWorld();
        if (recipeIndex > 0 && recipeIndex <= size) {
            this.selectedRecipeIndex = recipeIndex;
            AbstractCookingRecipe recipe =
            this.currentMatchingRecipes.get(recipeIndex - 1);
            inv.data.overriddenRecipes.put(new KeyableItemStack(
                toFind.copyWithCount(1)
            ), recipe.getId());
            this.prevRecipeBtn = makeTooltipItem(new ItemStack(
                this.prevRecipeItem, 1
            ), this.config.getGuiPrevRecipeItemTitle().get(inv.furnaceType),
            String.format(this.config.getGuiPrevRecipeItemTooltip()
                          .get(inv.furnaceType), recipeIndex, size));
            this.nextRecipeBtn = makeTooltipItem(new ItemStack(
                this.nextRecipeItem, 1
            ), this.config.getGuiNextRecipeItemTitle().get(inv.furnaceType),
            String.format(this.config.getGuiNextRecipeItemTooltip()
                          .get(inv.furnaceType), recipeIndex, size));
            this.recipeResultPreview =
            world == null ? ItemStack.EMPTY : recipe.getOutput(
                world.getRegistryManager()
            );
        } else {
            this.selectedRecipeIndex = 0;
            inv.data.overriddenRecipes.remove(new KeyableItemStack(
                toFind.copyWithCount(1)
            ));
            this.prevRecipeBtn = makeTooltipItem(new ItemStack(
                this.prevRecipeItem, 1
            ), this.config.getGuiPrevRecipeItemTitle().get(inv.furnaceType),
            String.format(this.config.getGuiPrevRecipeArbitraryItemTooltip()
                          .get(inv.furnaceType), size));
            this.nextRecipeBtn = makeTooltipItem(new ItemStack(
                this.nextRecipeItem, 1
            ), this.config.getGuiNextRecipeItemTitle().get(inv.furnaceType),
            String.format(this.config.getGuiNextRecipeArbitraryItemTooltip()
                          .get(inv.furnaceType), size));
            AbstractCookingRecipe recipe =
            FureamFurnaceLogic.findRecipe(world, toFind, inv.recipeType)
            .orElse(null);
            this.recipeResultPreview =
            world == null || recipe == null ? ItemStack.EMPTY :
            recipe.getOutput(world.getRegistryManager());
        }
    }

    public void updateMatchingRecipes(FureamScreenInventory inv) {
        this.currentMatchingRecipes.clear();
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (!toFind.isEmpty()) {
            World world = inv.furnace.getWorld();
            if (world != null) {
                this.currentMatchingRecipes.addAll(
                    world.getRecipeManager().getAllMatches(
                        inv.recipeType,
                        FureamFurnaceLogic.singleSlotInventory(toFind), world
                    )
                );
                this.currentMatchingRecipes
                .sort(Comparator.comparing(AbstractCookingRecipe::getId));
            }
        }
    }

    public void updateRecipeSettingArea(FureamScreenInventory inv) {
        this.updateMatchingRecipes(inv);
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (toFind.isEmpty()) {
            this.switchSelectedRecipeIndex(inv, 0);
        } else {
            Identifier identifier = inv.data.overriddenRecipes.get(
                new KeyableItemStack(toFind.copyWithCount(1))
            );
            boolean found = false;
            int i = 1;
            for (AbstractCookingRecipe recipe : this.currentMatchingRecipes) {
                if (recipe.getId().equals(identifier)) {
                    this.switchSelectedRecipeIndex(inv, i);
                    found = true;
                    break;
                }
                ++i;
            }
            if (!found) {
                inv.data.overriddenRecipes
                .remove(new KeyableItemStack(toFind.copyWithCount(1)));
                this.switchSelectedRecipeIndex(inv, 0);
            }
        }
    }

    @Override
    public void refreshVisuals(FureamScreenInventory inv) {
        if (inv.data.experience != this.cachedXp) {
            this.cachedXp = inv.data.experience;
            this.xpIndicator = makeTooltipItem(
                new ItemStack(this.xpIndicatorItem, 1), (
                    (int)inv.data.experience > 0 ?
                    this.config.getGuiXpIndicatorGainableItemTitle() :
                    this.config.getGuiXpIndicatorItemTitle()
                ).get(inv.furnaceType), String.format(
                    this.config.getGuiXpIndicatorItemTooltip()
                    .get(inv.furnaceType), this.cachedXp
                )
            );
        }
    }
}
