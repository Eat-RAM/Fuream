package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.KeyableItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

    public final @NotNull ArrayList<@NotNull CookingRecipe<?>> currentMatchingRecipes = new ArrayList<>();
    private @Range(from = 0, to = Integer.MAX_VALUE) int selectedRecipeIndex;
    private float cachedXp = -1f;

    public RecipeSettingAndXpFunctionalArea(
        FureamScreenInventory inv, FureamWorldConfig config
    ) {
        this.config = config;
        FurnaceType type = inv.furnaceType;

        Material prevMat = parseMaterial(config != null ? config.getGuiPrevRecipeItemId().get(type) : null, Material.BOOK);
        Material nextMat = parseMaterial(config != null ? config.getGuiNextRecipeItemId().get(type) : null, Material.BOOK);
        Material xpMat = parseMaterial(config != null ? config.getGuiXpIndicatorItemId().get(type) : null, Material.LIME_STAINED_GLASS_PANE);

        this.prevRecipeItem = prevMat;
        this.nextRecipeItem = nextMat;
        this.xpIndicatorItem = xpMat;
        this.updateRecipeSettingArea(inv);
        this.refreshVisuals(inv);
    }

    private static Material parseMaterial(@Nullable String id, Material fallback) {
        if (id == null) return fallback;
        String name = id.startsWith("minecraft:") ? id.substring(10) : id;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : fallback;
    }

    @Override
    @Contract(pure = true)
    public @NotNull ItemStack getGuiStack(FureamScreenInventory inv, int slot) {
        switch (slot) {
            case 0: return inv.data.recipeOverridingInput != null ? inv.data.recipeOverridingInput : new ItemStack(Material.AIR);
            case 1: return this.prevRecipeBtn;
            case 2: return this.nextRecipeBtn;
            case 3: return this.recipeResultPreview;
            case 4: return this.xpIndicator;
            default: return inv.border;
        }
    }

    @Override
    public void setGuiStack(FureamScreenInventory inv, int slot, ItemStack newStack) {
        if (slot == 0) {
            inv.data.recipeOverridingInput = newStack != null ? newStack : new ItemStack(Material.AIR);
            this.updateRecipeSettingArea(inv);
        }
    }

    @Override
    public void onGuiClick(
        SlotClick slotClick, FureamScreenInventory display, int slot,
        int button, ClickType clickType, Player player
    ) {
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
        return slot == 0;
    }

    @Override
    @Contract(pure = true)
    public boolean isTakable(FureamScreenInventory inv, int slot) {
        return slot == 0;
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getSelectedRecipeIndex() {
        return this.selectedRecipeIndex;
    }

    public void switchSelectedRecipeIndex(FureamScreenInventory inv, int recipeIndex) {
        ItemStack toFind = inv.data.recipeOverridingInput;
        FurnaceType type = inv.furnaceType;
        if (toFind == null || toFind.getType().isAir()) {
            this.selectedRecipeIndex = 0;
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                config != null ? config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                config != null ? config.getGuiPrevRecipeEmptyItemTooltip().get(type) : "Please insert an item into the slot on the left to adjust"
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                config != null ? config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                config != null ? config.getGuiNextRecipeEmptyItemTooltip().get(type) : "Please insert an item into the slot on the left to adjust"
            );
            this.recipeResultPreview = new ItemStack(Material.AIR);
            return;
        }
        int size = this.currentMatchingRecipes.size();
        if (recipeIndex > 0 && recipeIndex <= size) {
            this.selectedRecipeIndex = recipeIndex;
            CookingRecipe<?> recipe = this.currentMatchingRecipes.get(recipeIndex - 1);
            inv.data.overriddenRecipes.put(new KeyableItemStack(toFind), recipe.getKey());
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                config != null ? config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                String.format(config != null ? config.getGuiPrevRecipeItemTooltip().get(type) : "Current: %d / %d", recipeIndex, size)
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                config != null ? config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                String.format(config != null ? config.getGuiNextRecipeItemTooltip().get(type) : "Current: %d / %d", recipeIndex, size)
            );
            this.recipeResultPreview = recipe.getResult().clone();
        } else {
            this.selectedRecipeIndex = 0;
            inv.data.overriddenRecipes.remove(new KeyableItemStack(toFind));
            this.prevRecipeBtn = makeTooltipItem(
                new ItemStack(this.prevRecipeItem, 1),
                config != null ? config.getGuiPrevRecipeItemTitle().get(type) : "Previous Recipe",
                String.format(config != null ? config.getGuiPrevRecipeArbitraryItemTooltip().get(type) : "Current: arbitrary of %d", size)
            );
            this.nextRecipeBtn = makeTooltipItem(
                new ItemStack(this.nextRecipeItem, 1),
                config != null ? config.getGuiNextRecipeItemTitle().get(type) : "Next Recipe",
                String.format(config != null ? config.getGuiNextRecipeArbitraryItemTooltip().get(type) : "Current: arbitrary of %d", size)
            );
            CookingRecipe<?> recipe = FureamFurnaceLogic.findRecipe(displayWorld(inv), toFind, inv.furnaceType).orElse(null);
            this.recipeResultPreview = recipe != null ? recipe.getResult().clone() : new ItemStack(Material.AIR);
        }
    }

    private org.bukkit.World displayWorld(FureamScreenInventory inv) {
        return inv.location != null ? inv.location.getWorld() : null;
    }

    public void updateMatchingRecipes(FureamScreenInventory inv) {
        this.currentMatchingRecipes.clear();
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (toFind != null && !toFind.getType().isAir()) {
            List<CookingRecipe<?>> matches = FureamFurnaceLogic.findAllMatches(displayWorld(inv), toFind, inv.furnaceType);
            matches.sort(Comparator.comparing(r -> r.getKey().toString()));
            this.currentMatchingRecipes.addAll(matches);
        }
    }

    public void updateRecipeSettingArea(FureamScreenInventory inv) {
        this.updateMatchingRecipes(inv);
        ItemStack toFind = inv.data.recipeOverridingInput;
        if (toFind == null || toFind.getType().isAir()) {
            this.switchSelectedRecipeIndex(inv, 0);
        } else {
            NamespacedKey overridden = inv.data.overriddenRecipes.get(new KeyableItemStack(toFind));
            boolean found = false;
            int i = 1;
            for (CookingRecipe<?> recipe : this.currentMatchingRecipes) {
                if (recipe.getKey().equals(overridden)) {
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
                (config != null ? config.getGuiXpIndicatorGainableItemTitle().get(type) : "XP (click to gain)") :
                (config != null ? config.getGuiXpIndicatorItemTitle().get(type) : "XP");
            String tooltip = String.format(
                config != null ? config.getGuiXpIndicatorItemTooltip().get(type) : "Current: %.1f",
                this.cachedXp
            );
            this.xpIndicator = makeTooltipItem(new ItemStack(this.xpIndicatorItem, 1), title, tooltip);
        }
    }
}
