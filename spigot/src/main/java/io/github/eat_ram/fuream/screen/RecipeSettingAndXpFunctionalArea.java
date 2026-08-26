package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.List;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.compat.RecipeHandle;
import io.github.eat_ram.fuream.compat.VersionAdapters;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import static io.github.eat_ram.fuream.screen.FureamScreenInventory.makeTooltipItem;

public class RecipeSettingAndXpFunctionalArea implements FureamFunctionalArea {
    private final FureamWorldConfig config;
    public final @NotNull Material prevRecipeItem;
    public final @NotNull Material nextRecipeItem;
    public final @NotNull Material xpIndicatorItem;

    /** Slot 1 (46 in {@link FureamScreenInventory}). */
    public @NotNull ItemStack prevRecipeBtn = new ItemStack(Material.AIR);
    /** Slot 2 (47 in {@link FureamScreenInventory}). */
    public @NotNull ItemStack nextRecipeBtn = new ItemStack(Material.AIR);
    /** Slot 3 (48 in {@link FureamScreenInventory}). */
    public @NotNull ItemStack recipeResultPreview = new ItemStack(Material.AIR);
    /** Slot 4 (49 in {@link FureamScreenInventory}). */
    public @NotNull ItemStack xpIndicator = new ItemStack(Material.AIR);

    public final @NotNull ArrayList<@NotNull RecipeHandle> currentMatchingRecipes = new ArrayList<>();
    private final boolean recipeSelectionAvailable;
    private @Range(from = 0, to = Integer.MAX_VALUE) int selectedRecipeIndex;
    private float cachedXp = -1f;

    public RecipeSettingAndXpFunctionalArea(
        FureamScreenInventory inv, FureamWorldConfig config
    ) {
        this.config = config;
        this.recipeSelectionAvailable = VersionAdapters.current().supportsRecipeOverrides();
        FurnaceType type = inv.furnaceType;

        Material prevMat = parseMaterial(config != null ? config.getGuiPrevRecipeItemId().get(type) : null, Material.BOOK);
        Material nextMat = parseMaterial(config != null ? config.getGuiNextRecipeItemId().get(type) : null, Material.BOOK);
        Material xpMat = parseMaterial(config != null ? config.getGuiXpIndicatorItemId().get(type) : null, Material.GLASS_PANE);

        this.prevRecipeItem = prevMat;
        this.nextRecipeItem = nextMat;
        this.xpIndicatorItem = xpMat;
        this.updateRecipeSettingArea(inv);
        this.refreshVisuals(inv);
    }

    private static Material parseMaterial(@Nullable String id, Material fallback) {
        return ItemCompat.matchMaterial(id, fallback);
    }

    @Override
    @Contract(pure = true)
    public @NotNull ItemStack getGuiStack(FureamScreenInventory inv, int slot) {
        switch (slot) {
            case 0: return this.recipeSelectionAvailable ? inv.data.recipeOverridingInput : inv.border;
            case 1: return this.recipeSelectionAvailable ? this.prevRecipeBtn : inv.border;
            case 2: return this.recipeSelectionAvailable ? this.nextRecipeBtn : inv.border;
            case 3: return this.recipeSelectionAvailable ? this.recipeResultPreview : inv.border;
            case 4: return this.xpIndicator;
            default: return inv.border;
        }
    }

    @Override
    public void setGuiStack(FureamScreenInventory inv, int slot, ItemStack newStack) {
        if (this.recipeSelectionAvailable && slot == 0) {
            inv.data.recipeOverridingInput = newStack != null ? newStack : new ItemStack(Material.AIR);
            this.updateRecipeSettingArea(inv);
        }
    }

    @Override
    public void onGuiClick(
        SlotClick slotClick, FureamScreenInventory display, int slot,
        int button, ClickType clickType, Player player
    ) {
        if (!this.recipeSelectionAvailable && slot != 4) return;
        switch (slot) {
            case 0:
                slotClick.onSlotClick(45, button, clickType, player);
                this.updateRecipeSettingArea(display);
                break;
            case 1: {
                int curRecipeIndex = this.getSelectedRecipeIndex();
                this.switchSelectedRecipeIndex(
                    display, curRecipeIndex > 0 ? curRecipeIndex - 1 : this.currentMatchingRecipes.size()
                );
                break;
            }
            case 2: {
                int curRecipeIndex = this.getSelectedRecipeIndex();
                this.switchSelectedRecipeIndex(
                    display, curRecipeIndex < this.currentMatchingRecipes.size() ? curRecipeIndex + 1 : 0
                );
                break;
            }
            case 4:
                FureamFurnaceLogic.grantExperience(player, display.location, display.data);
                this.refreshVisuals(display);
                break;
        }
    }

    @Override
    @Contract(pure = true)
    public boolean isStorable(FureamScreenInventory inv, int slot) {
        return this.recipeSelectionAvailable && slot == 0;
    }

    @Override
    @Contract(pure = true)
    public boolean isTakable(FureamScreenInventory inv, int slot) {
        return this.recipeSelectionAvailable && slot == 0;
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getSelectedRecipeIndex() {
        return this.selectedRecipeIndex;
    }

    public void switchSelectedRecipeIndex(FureamScreenInventory inv, int recipeIndex) {
        ItemStack toFind = inv.data.recipeOverridingInput;
        FurnaceType type = inv.furnaceType;
        if (!this.recipeSelectionAvailable || ItemCompat.isEmpty(toFind)) {
            this.selectedRecipeIndex = 0;
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                    this.config != null ? this.config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                    this.config != null ? this.config.getGuiPrevRecipeEmptyItemTooltip().get(type) : "Please insert an item into the slot on the left to adjust"
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                    this.config != null ? this.config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                    this.config != null ? this.config.getGuiNextRecipeEmptyItemTooltip().get(type) : "Please insert an item into the slot on the left to adjust"
            );
            this.recipeResultPreview = new ItemStack(Material.AIR);
            return;
        }
        int size = this.currentMatchingRecipes.size();
        if (recipeIndex > 0 && recipeIndex <= size) {
            this.selectedRecipeIndex = recipeIndex;
            RecipeHandle recipe = this.currentMatchingRecipes.get(recipeIndex - 1);
            inv.data.overriddenRecipes.put(new KeyableItemStack(toFind), recipe.id);
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                    this.config != null ? this.config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                String.format(this.config != null ? this.config.getGuiPrevRecipeItemTooltip().get(type) : "Current: %d / %d", recipeIndex, size)
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                    this.config != null ? this.config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                String.format(this.config != null ? this.config.getGuiNextRecipeItemTooltip().get(type) : "Current: %d / %d", recipeIndex, size)
            );
            this.recipeResultPreview = recipe.result.clone();
        } else {
            this.selectedRecipeIndex = 0;
            inv.data.overriddenRecipes.remove(new KeyableItemStack(toFind));
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                    this.config != null ? this.config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                String.format(this.config != null ? this.config.getGuiPrevRecipeArbitraryItemTooltip().get(type) : "Current: arbitrary of %d", size)
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                    this.config != null ? this.config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                String.format(this.config != null ? this.config.getGuiNextRecipeArbitraryItemTooltip().get(type) : "Current: arbitrary of %d", size)
            );
            RecipeHandle recipe = FureamFurnaceLogic.findRecipe(this.displayWorld(inv), toFind, inv.furnaceType).orElse(null);
            this.recipeResultPreview = recipe != null ? recipe.result.clone() : new ItemStack(Material.AIR);
        }
    }

    private org.bukkit.World displayWorld(FureamScreenInventory inv) {
        return inv.location.getWorld();
    }

    public void updateMatchingRecipes(FureamScreenInventory inv) {
        this.currentMatchingRecipes.clear();
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (this.recipeSelectionAvailable && !ItemCompat.isEmpty(toFind)) {
            List<RecipeHandle> matches = FureamFurnaceLogic.findAllMatches(this.displayWorld(inv), toFind, inv.furnaceType);
            this.currentMatchingRecipes.addAll(matches);
        }
    }

    public void updateRecipeSettingArea(FureamScreenInventory inv) {
        this.updateMatchingRecipes(inv);
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (!this.recipeSelectionAvailable || ItemCompat.isEmpty(toFind)) {
            this.switchSelectedRecipeIndex(inv, 0);
        } else {
            String overridden = inv.data.overriddenRecipes.get(new KeyableItemStack(toFind));
            boolean found = false;
            int i = 1;
            for (RecipeHandle recipe : this.currentMatchingRecipes) {
                if (recipe.id.equals(overridden)) {
                    this.switchSelectedRecipeIndex(inv, i);
                    found = true;
                    break;
                }
                ++i;
            }
            if (!found) {
                inv.data.overriddenRecipes.remove(new KeyableItemStack(toFind));
                this.switchSelectedRecipeIndex(inv, 0);
            }
        }
    }

    @Override
    public void refreshVisuals(FureamScreenInventory inv) {
        if (inv.data.experience != this.cachedXp) {
            this.cachedXp = inv.data.experience;
            FurnaceType type = inv.furnaceType;
            String title = ((int) inv.data.experience > 0) ?
                (this.config != null ? this.config.getGuiXpIndicatorGainableItemTitle().get(type) : "XP (click to gain)") :
                (this.config != null ? this.config.getGuiXpIndicatorItemTitle().get(type) : "XP");
            String tooltip = String.format(
                    this.config != null ? this.config.getGuiXpIndicatorItemTooltip().get(type) : "Current: %.1f",
                this.cachedXp
            );
            this.xpIndicator = makeTooltipItem(new ItemStack(this.xpIndicatorItem, 1), title, tooltip);
        }
    }
}
