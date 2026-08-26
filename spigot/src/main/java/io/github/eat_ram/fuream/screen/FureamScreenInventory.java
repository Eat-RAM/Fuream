package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * The 54-slot view representing the virtual furnace GUI.
 *
 * <p>Row layout:
 * <pre>
 *  0..8   inputs (row 1)
 *  9..17  fuel remaining bar (row 2)
 * 18..26  fuels (row 3)
 * 27..35  smelt progress bar (row 4)
 * 36..44  outputs (row 5)
 * 45..53  functional area & decor (row 6)
 * </pre>
 */
public class FureamScreenInventory {
    public static final int COLS = 9;
    public static final int SIZE = 54;
    public static final ArrayList<@NotNull BiFunction<
        ? super FureamScreenInventory, ? super FureamWorldConfig,
        ? extends @NotNull FureamFunctionalArea
    >> FUNCTIONAL_AREA_REGISTRY = new ArrayList<>();

    public final @NotNull FurnaceType furnaceType;
    public final @Nullable FureamWorldConfig config;
    public final @NotNull ItemStack border;
    public final @NotNull Material fuelLeftItem;
    public final @NotNull Material fuelUsedItem;
    public final @NotNull Material progressDoneItem;
    public final @NotNull Material progressRemainingItem;
    public final @NotNull ArrayList<@NotNull FureamFunctionalArea> functionalAreas;
    private @Range(from = 0, to = Integer.MAX_VALUE) int currentFunctionalArea;
    public final @NotNull Material nextFunctionalItem;
    public final @NotNull Material prevPageItem;
    public final @NotNull Material nextPageItem;

    public final @Range(from = 1, to = Integer.MAX_VALUE) int inputSlots;
    public final @Range(from = 1, to = Integer.MAX_VALUE) int fuelSlots;
    public final @Range(from = 1, to = Integer.MAX_VALUE) int outputSlots;
    public final @NotNull Location location;
    public final @NotNull FureamFurnaceData data;

    public int burnTime;
    public int fuelTimeTotal;
    public int cookTime;
    public int cookTimeTotal = 200;

    public final @NotNull ItemStack @NotNull[] fuelBar = new ItemStack[COLS];
    public final @NotNull ItemStack @NotNull[] progBar = new ItemStack[COLS];
    public @NotNull ItemStack nextFunctionalBtn = new ItemStack(Material.AIR);
    public @NotNull ItemStack prevPageBtn = new ItemStack(Material.AIR);
    public @NotNull ItemStack nextPageBtn = new ItemStack(Material.AIR);
    private @Range(from = 0, to = Integer.MAX_VALUE) int page;
    public final @NotNull Inventory bukkitInventory;

    public FureamScreenInventory(
        @NotNull Location location, @NotNull FurnaceType type,
        @NotNull FureamFurnaceData data, @Nullable FureamWorldConfig config
    ) {
        this.location = location;
        this.furnaceType = type;
        this.data = data;
        this.config = config;
        this.functionalAreas = new ArrayList<>();

        int inputSlotCount = config != null ? config.getInputSlotCount().get(type) : 9;
        int fuelSlotCount = config != null ? config.getFuelSlotCount().get(type) : 9;
        int outputSlotCount = config != null ? config.getOutputSlotCount().get(type) : 9;

        this.inputSlots = Math.max(1, inputSlotCount);
        this.fuelSlots = Math.max(1, fuelSlotCount);
        this.outputSlots = Math.max(1, outputSlotCount);

        this.fuelLeftItem = parseMaterial(config != null ? config.getGuiFuelLeftItemId().get(type) : null, Material.ORANGE_STAINED_GLASS_PANE);
        this.fuelUsedItem = parseMaterial(config != null ? config.getGuiFuelUsedItemId().get(type) : null, Material.BLACK_STAINED_GLASS_PANE);
        this.progressDoneItem = parseMaterial(config != null ? config.getGuiProgressDoneItemId().get(type) : null, Material.WHITE_STAINED_GLASS_PANE);
        this.progressRemainingItem = parseMaterial(config != null ? config.getGuiProgressRemainingItemId().get(type) : null, Material.BLACK_STAINED_GLASS_PANE);
        this.nextFunctionalItem = parseMaterial(config != null ? config.getGuiNextFunctionalAreaItemId().get(type) : null, Material.COMMAND_BLOCK);
        this.prevPageItem = parseMaterial(config != null ? config.getGuiPrevPageItemId().get(type) : null, Material.ARROW);
        this.nextPageItem = parseMaterial(config != null ? config.getGuiNextPageItemId().get(type) : null, Material.ARROW);

        Material borderMat = parseMaterial(config != null ? config.getGuiBorderItemId().get(type) : null, Material.BLACK_STAINED_GLASS_PANE);
        this.border = makeTooltipItem(
            new ItemStack(borderMat, 1),
            config != null ? config.getGuiBorderItemTitle().get(type) : "GUI Border"
        );

        String title = type == FurnaceType.SMOKER ? "Smoker" : (type == FurnaceType.BLAST_FURNACE ? "Blast Furnace" : "Furnace");
        this.bukkitInventory = Bukkit.createInventory(null, 54, title);

        this.cookTimeTotal = this.computeCookTimeTotal();
        this.switchPage(0);
        for (BiFunction<
            ? super FureamScreenInventory, ? super FureamWorldConfig,
            ? extends FureamFunctionalArea
        > factory : FUNCTIONAL_AREA_REGISTRY) {
            this.functionalAreas.add(factory.apply(this, this.config));
        }

        this.nextFunctionalBtn = this.functionalAreas.size() < 2 ? this.border.clone() :
            makeTooltipItem(
                new ItemStack(this.nextFunctionalItem, 1),
                config != null ? config.getGuiNextFunctionalAreaItemTitle().get(type) : "Next Functional Area"
            );

        this.refreshVisuals();
    }

    private static Material parseMaterial(@Nullable String id, Material fallback) {
        if (id == null) return fallback;
        String name = id.startsWith("minecraft:") ? id.substring(10) : id;
        Material mat = Material.matchMaterial(name.toUpperCase());
        return mat != null ? mat : fallback;
    }

    public int computeCookTimeTotal() {
        FurnacePos pos = new FurnacePos(this.location);
        FurnaceContext ctx = FurnaceManager.CONTEXTS.get(pos);
        if (ctx != null) {
            return Math.max(1, ctx.cookTimeTotal);
        }
        return this.furnaceType == FurnaceType.FURNACE ? 200 : 100;
    }

    public boolean isInputSlot(int slot) {
        return slot >= 0 && slot < 9 && this.page * 9 + slot < this.inputSlots;
    }

    public boolean isFuelRowSlot(int slot) {
        return slot >= 18 && slot < 27 && this.page * 9 + slot - 18 < this.fuelSlots;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= 36 && slot < 45 && this.page * 9 + slot - 36 < this.outputSlots;
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

    public void refreshVisuals() {
        this.cookTimeTotal = this.computeCookTimeTotal();
        if (this.cookTime >= this.cookTimeTotal) {
            this.cookTime = Math.max(0, this.cookTimeTotal - 1);
        }

        int fuelCount = barCount(this.burnTime, this.fuelTimeTotal);
        int progressCount = barCount(this.cookTime, this.cookTimeTotal);

        for (int i = 0; i < COLS; ++i) {
            this.fuelBar[i] = this.addFuelTooltip(new ItemStack(
                i < fuelCount ? this.fuelLeftItem : this.fuelUsedItem, 1
            ));
            this.progBar[i] = this.addProgressTooltip(new ItemStack(
                i < progressCount ? this.progressDoneItem : this.progressRemainingItem, 1
            ));
        }

        for (FureamFunctionalArea area : this.functionalAreas) {
            area.refreshVisuals(this);
        }

        this.syncToBukkitInventory();
    }

    public void syncToBukkitInventory() {
        for (int i = 0; i < 54; i++) {
            ItemStack stack = this.getStack(i);
            this.bukkitInventory.setItem(i, stack);
        }
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getPage() {
        return this.page;
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getMaxPage() {
        int maxSlots = Math.max(this.inputSlots, Math.max(this.fuelSlots, this.outputSlots));
        return Math.max(0, (maxSlots - 1) / 9);
    }

    public void switchPage(int page) {
        if (page >= 0 && page <= this.getMaxPage()) {
            this.page = page;
            this.prevPageBtn = makeTooltipItem(
                new ItemStack(this.prevPageItem, 1),
                    this.config != null ? this.config.getGuiPrevPageItemTitle().get(this.furnaceType) : "Previous Page",
                String.format(this.config != null ? this.config.getGuiPrevPageItemTooltip().get(this.furnaceType) : "Page %d", page + 1)
            );
            this.nextPageBtn = makeTooltipItem(
                new ItemStack(this.nextPageItem, 1),
                    this.config != null ? this.config.getGuiNextPageItemTitle().get(this.furnaceType) : "Next Page",
                String.format(this.config != null ? this.config.getGuiNextPageItemTooltip().get(this.furnaceType) : "Page %d", page + 1)
            );
            this.syncToBukkitInventory();
        }
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getCurrentFunctionalArea() {
        return this.currentFunctionalArea;
    }

    public void setCurrentFunctionalArea(int currentFunctionalArea) {
        this.currentFunctionalArea = Math.max(0, currentFunctionalArea);
        this.syncToBukkitInventory();
    }

    @Contract(pure = true)
    private static @Range(from = 0, to = Integer.MAX_VALUE) int barCount(int value, int total) {
        if (total <= 0 || value <= 0) return 0;
        int k = Math.round(9f * value / (float) total);
        return Math.max(0, Math.min(COLS, k));
    }

    private @NotNull ItemStack addFuelTooltip(@NotNull ItemStack stack) {
        String title = this.config != null ? this.config.getGuiFuelItemTitle().get(this.furnaceType) : "Fuel";
        String lore = String.format(
                this.config != null ? this.config.getGuiFuelItemTooltip().get(this.furnaceType) : "Available: %d / %d",
            this.burnTime, this.fuelTimeTotal
        );
        return makeTooltipItem(stack, title, lore);
    }

    private @NotNull ItemStack addProgressTooltip(@NotNull ItemStack stack) {
        String title = this.config != null ? this.config.getGuiProgressItemTitle().get(this.furnaceType) : "Progress";
        String lore = String.format(
                this.config != null ? this.config.getGuiProgressItemTooltip().get(this.furnaceType) : "%d / %d",
            this.cookTime, this.cookTimeTotal
        );
        return makeTooltipItem(stack, title, lore);
    }

    public static @NotNull ItemStack makeTooltipItem(
        @NotNull ItemStack stack, @Nullable String title,
        @NotNull String @NotNull... lores
    ) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (title != null) {
                meta.setDisplayName("§r" + title);
            }
            if (lores.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String lore : lores) {
                    loreList.add("§7" + lore);
                }
                meta.setLore(loreList);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public @NotNull ItemStack getStack(int slot) {
        if (this.isInputSlot(slot)) {
            int idx = this.page * 9 + slot;
            return idx < this.data.inputs.size() ? this.data.inputs.get(idx) : new ItemStack(Material.AIR);
        }
        if (this.isFuelRowSlot(slot)) {
            int idx = this.page * 9 + slot - 18;
            return idx < this.data.fuels.size() ? this.data.fuels.get(idx) : new ItemStack(Material.AIR);
        }
        if (this.isOutputSlot(slot)) {
            int idx = this.page * 9 + slot - 36;
            return idx < this.data.outputs.size() ? this.data.outputs.get(idx) : new ItemStack(Material.AIR);
        }
        if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            return ca < this.functionalAreas.size() ?
                this.functionalAreas.get(ca).getGuiStack(this, slot - 45) : this.border;
        }
        if (slot >= 9 && slot < 18) {
            return this.fuelBar[slot - 9];
        }
        if (slot >= 27 && slot < 36) {
            return this.progBar[slot - 27];
        }
        if (slot == 51) return this.nextFunctionalBtn;
        if (slot == 52) return this.prevPageBtn;
        if (slot == 53) return this.nextPageBtn;
        return this.border;
    }

    public void setStack(int slot, @NotNull ItemStack stack) {
        if (this.isInputSlot(slot)) {
            int idx = this.page * 9 + slot;
            while (this.data.inputs.size() <= idx) {
                this.data.inputs.add(new ItemStack(Material.AIR));
            }
            this.data.inputs.set(idx, stack);
        } else if (this.isFuelRowSlot(slot)) {
            int idx = this.page * 9 + slot - 18;
            while (this.data.fuels.size() <= idx) {
                this.data.fuels.add(new ItemStack(Material.AIR));
            }
            this.data.fuels.set(idx, stack);
        } else if (this.isOutputSlot(slot)) {
            int idx = this.page * 9 + slot - 36;
            while (this.data.outputs.size() <= idx) {
                this.data.outputs.add(new ItemStack(Material.AIR));
            }
            this.data.outputs.set(idx, stack);
        } else if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            if (ca < this.functionalAreas.size()) {
                FureamFunctionalArea area = this.functionalAreas.get(ca);
                if (area.isStorable(this, slot - 45)) {
                    area.setGuiStack(this, slot - 45, stack);
                }
            }
        }
    }

    public boolean isValid(int slot, @NotNull ItemStack stack) {
        if (!stack.getType().isAir()) {
            if (this.isInputSlot(slot)) return true;
            if (this.isFuelRowSlot(slot)) return FureamFurnaceLogic.isFuel(stack);
        }
        return false;
    }

    static {
        FUNCTIONAL_AREA_REGISTRY.add(RecipeSettingAndXpFunctionalArea::new);
    }
}
