package io.github.eat_ram.fuream.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.UUID;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.ResolvedFurnaceConfig;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceManager.FurnaceContext;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;
import io.github.eat_ram.fuream.compat.ItemCompat;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
public final class FureamScreenInventory implements InventoryHolder {
    public static final int COLS = 9;
    public static final int SIZE = 54;
    public static final int INPUT_START = 0;
    public static final int FUEL_BAR_START = 9;
    public static final int FUEL_START = 18;
    public static final int PROGRESS_BAR_START = 27;
    public static final int OUTPUT_START = 36;
    public static final int FUNCTIONAL_START = 45;
    public static final int NEXT_FUNCTIONAL_SLOT = 51;
    public static final int PREVIOUS_PAGE_SLOT = 52;
    public static final int NEXT_PAGE_SLOT = 53;
    private static final Set<String> WARNED_FORMATS = new HashSet<>();
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
    public final @NotNull UUID viewerId;
    public final @NotNull FureamScreenHandler handler;

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
    private int visualTick;
    private boolean forceBarRefresh = true;

    public FureamScreenInventory(
        @NotNull UUID viewerId, @NotNull Location location, @NotNull FurnaceType type,
        @NotNull FureamFurnaceData data, @Nullable FureamWorldConfig config
    ) {
        this.location = location;
        this.viewerId = viewerId;
        this.furnaceType = type;
        this.data = data;
        this.config = config;
        this.functionalAreas = new ArrayList<>();

        ResolvedFurnaceConfig resolved = config == null
            ? null : ResolvedFurnaceConfig.resolve(config, type);
        this.inputSlots = resolved == null ? 9 : resolved.inputSlots;
        this.fuelSlots = resolved == null ? 9 : resolved.fuelSlots;
        this.outputSlots = resolved == null ? 9 : resolved.outputSlots;

        this.fuelLeftItem = parseMaterial(config != null ? config.getGuiFuelLeftItemId().get(type) : null, Material.GLASS_PANE);
        this.fuelUsedItem = parseMaterial(config != null ? config.getGuiFuelUsedItemId().get(type) : null, Material.GLASS_PANE);
        this.progressDoneItem = parseMaterial(config != null ? config.getGuiProgressDoneItemId().get(type) : null, Material.GLASS_PANE);
        this.progressRemainingItem = parseMaterial(config != null ? config.getGuiProgressRemainingItemId().get(type) : null, Material.GLASS_PANE);
        this.nextFunctionalItem = parseMaterial(config != null ? config.getGuiNextFunctionalAreaItemId().get(type) : null, Material.BOOK);
        this.prevPageItem = parseMaterial(config != null ? config.getGuiPrevPageItemId().get(type) : null, Material.ARROW);
        this.nextPageItem = parseMaterial(config != null ? config.getGuiNextPageItemId().get(type) : null, Material.ARROW);

        Material borderMat = parseMaterial(config != null ? config.getGuiBorderItemId().get(type) : null, Material.GLASS_PANE);
        this.border = makeTooltipItem(
            new ItemStack(borderMat, 1),
            config != null ? config.getGuiBorderItemTitle().get(type) : "GUI Border"
        );

        String title = resolveTitle(location, type, config);
        this.bukkitInventory = Bukkit.createInventory(this, SIZE, title.length() > 32 ? title.substring(0, 32) : title);
        this.handler = new FureamScreenHandler(this);

        FurnacePos pos = new FurnacePos(this.location);
        FurnaceContext ctx = FurnaceManager.getContext(pos);
        if (ctx != null) {
            this.burnTime = ctx.burnTime;
            this.fuelTimeTotal = Math.max(1, ctx.fuelTimeTotal);
            this.cookTime = ctx.cookTime;
            this.cookTimeTotal = Math.max(1, ctx.cookTimeTotal);
        } else {
            this.cookTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
            this.fuelTimeTotal = 200;
        }
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
        return ItemCompat.matchMaterial(id, fallback);
    }

    private static String resolveTitle(
        Location location, FurnaceType type, @Nullable FureamWorldConfig config
    ) {
        try {
            Object state = location.getBlock().getState();
            Object custom = state.getClass().getMethod("getCustomName").invoke(state);
            if (custom instanceof String && !((String) custom).isEmpty()) return (String) custom;
        } catch (ReflectiveOperationException ignored) {
        }
        if (config != null) {
            String configured = config.getGuiTitle().get(type);
            if (configured != null && !configured.isEmpty()) return configured;
        }
        return type == FurnaceType.SMOKER ? "Smoker" :
            (type == FurnaceType.BLAST_FURNACE ? "Blast Furnace" : "Furnace");
    }

    @Override
    public Inventory getInventory() {
        return this.bukkitInventory;
    }

    public boolean isInputSlot(int slot) {
        return slot >= INPUT_START && slot < FUEL_BAR_START && this.page * COLS + slot < this.inputSlots;
    }

    public boolean isFuelRowSlot(int slot) {
        return slot >= FUEL_START && slot < PROGRESS_BAR_START &&
            this.page * COLS + slot - FUEL_START < this.fuelSlots;
    }

    public boolean isOutputSlot(int slot) {
        return slot >= OUTPUT_START && slot < FUNCTIONAL_START &&
            this.page * COLS + slot - OUTPUT_START < this.outputSlots;
    }

    public boolean isFunctionalAreaSlot(int slot) {
        return slot >= FUNCTIONAL_START && slot < NEXT_FUNCTIONAL_SLOT;
    }

    public boolean isBarOrDecorSlot(int slot) {
        return !(this.isInputSlot(slot) || this.isFuelRowSlot(slot) ||
                 this.isOutputSlot(slot) || this.isFunctionalAreaSlot(slot));
    }

    public int getInputSlotStop() {
        int diff = this.inputSlots - (this.page * COLS);
        return diff >= COLS ? COLS : (diff <= 0 ? 0 : diff);
    }

    public int getFuelSlotStop() {
        int diff = this.fuelSlots - (this.page * COLS);
        return diff >= COLS ? PROGRESS_BAR_START :
            (diff <= 0 ? FUEL_START : diff + FUEL_START);
    }

    public int getOutputSlotStop() {
        int diff = this.outputSlots - (this.page * COLS);
        return diff >= COLS ? FUNCTIONAL_START :
            (diff <= 0 ? OUTPUT_START : diff + OUTPUT_START);
    }

    public void refreshVisuals() {
        this.cookTimeTotal = Math.max(1, this.cookTimeTotal);
        if (this.cookTime >= this.cookTimeTotal) {
            this.cookTime = Math.max(0, this.cookTimeTotal - 1);
        }

        boolean refreshBars = this.forceBarRefresh || (this.visualTick++ & 1) == 0;
        if (refreshBars) {
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
            this.forceBarRefresh = false;
        }

        for (FureamFunctionalArea area : this.functionalAreas) {
            area.refreshVisuals(this);
        }

        this.syncToBukkitInventory();
    }

    public void syncToBukkitInventory() {
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = this.getStack(i);
            ItemStack current = this.bukkitInventory.getItem(i);
            if (!sameStack(current, stack)) {
                this.bukkitInventory.setItem(i, ItemCompat.isEmpty(stack) ? null : stack.clone());
            }
        }
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getPage() {
        return this.page;
    }

    @Contract(pure = true)
    public @Range(from = 0, to = Integer.MAX_VALUE) int getMaxPage() {
        int maxSlots = Math.max(this.inputSlots, Math.max(this.fuelSlots, this.outputSlots));
        return Math.max(0, (maxSlots - 1) / COLS);
    }

    public void switchPage(int page) {
        if (page >= 0 && page <= this.getMaxPage()) {
            this.page = page;
            this.forceBarRefresh = true;
            this.prevPageBtn = makeTooltipItem(
                new ItemStack(this.prevPageItem, 1),
                    this.config != null ? this.config.getGuiPrevPageItemTitle().get(this.furnaceType) : "Previous Page",
                safeFormat(this.config != null ? this.config.getGuiPrevPageItemTooltip().get(this.furnaceType) : "Page %d", "Page %d", page + 1)
            );
            this.nextPageBtn = makeTooltipItem(
                new ItemStack(this.nextPageItem, 1),
                    this.config != null ? this.config.getGuiNextPageItemTitle().get(this.furnaceType) : "Next Page",
                safeFormat(this.config != null ? this.config.getGuiNextPageItemTooltip().get(this.furnaceType) : "Page %d", "Page %d", page + 1)
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
        String lore = safeFormat(
            this.config != null ? this.config.getGuiFuelItemTooltip().get(this.furnaceType) : "Available: %d / %d",
            "Available: %d / %d", this.burnTime, this.fuelTimeTotal
        );
        return makeTooltipItem(stack, title, lore);
    }

    private @NotNull ItemStack addProgressTooltip(@NotNull ItemStack stack) {
        String title = this.config != null ? this.config.getGuiProgressItemTitle().get(this.furnaceType) : "Progress";
        String lore = safeFormat(
            this.config != null ? this.config.getGuiProgressItemTooltip().get(this.furnaceType) : "%d / %d",
            "%d / %d", this.cookTime, this.cookTimeTotal
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
            int idx = this.page * COLS + slot;
            return cloneOrAir(idx < this.data.inputs.size() ? this.data.inputs.get(idx) : null);
        }
        if (this.isFuelRowSlot(slot)) {
            int idx = this.page * COLS + slot - FUEL_START;
            return cloneOrAir(idx < this.data.fuels.size() ? this.data.fuels.get(idx) : null);
        }
        if (this.isOutputSlot(slot)) {
            int idx = this.page * COLS + slot - OUTPUT_START;
            return cloneOrAir(idx < this.data.outputs.size() ? this.data.outputs.get(idx) : null);
        }
        if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            return cloneOrAir(ca < this.functionalAreas.size() ?
                this.functionalAreas.get(ca).getGuiStack(this, slot - FUNCTIONAL_START) : this.border);
        }
        if (slot >= FUEL_BAR_START && slot < FUEL_START) {
            return cloneOrAir(this.fuelBar[slot - FUEL_BAR_START]);
        }
        if (slot >= PROGRESS_BAR_START && slot < OUTPUT_START) {
            return cloneOrAir(this.progBar[slot - PROGRESS_BAR_START]);
        }
        if (slot == NEXT_FUNCTIONAL_SLOT) return this.nextFunctionalBtn.clone();
        if (slot == PREVIOUS_PAGE_SLOT) return this.prevPageBtn.clone();
        if (slot == NEXT_PAGE_SLOT) return this.nextPageBtn.clone();
        return this.border.clone();
    }

    public void setStack(int slot, @NotNull ItemStack stack) {
        if (this.isInputSlot(slot)) {
            int idx = this.page * COLS + slot;
            while (this.data.inputs.size() <= idx) {
                this.data.inputs.add(new ItemStack(Material.AIR));
            }
            this.data.inputs.set(idx, cloneOrAir(stack));
        } else if (this.isFuelRowSlot(slot)) {
            int idx = this.page * COLS + slot - FUEL_START;
            while (this.data.fuels.size() <= idx) {
                this.data.fuels.add(new ItemStack(Material.AIR));
            }
            this.data.fuels.set(idx, cloneOrAir(stack));
        } else if (this.isOutputSlot(slot)) {
            int idx = this.page * COLS + slot - OUTPUT_START;
            while (this.data.outputs.size() <= idx) {
                this.data.outputs.add(new ItemStack(Material.AIR));
            }
            this.data.outputs.set(idx, cloneOrAir(stack));
        } else if (this.isFunctionalAreaSlot(slot)) {
            int ca = this.getCurrentFunctionalArea();
            if (ca < this.functionalAreas.size()) {
                FureamFunctionalArea area = this.functionalAreas.get(ca);
                if (area.isStorable(this, slot - FUNCTIONAL_START)) {
                    area.setGuiStack(this, slot - FUNCTIONAL_START, cloneOrAir(stack));
                }
            }
        }
    }

    public boolean isValid(int slot, @NotNull ItemStack stack) {
        if (!ItemCompat.isEmpty(stack)) {
            if (this.isInputSlot(slot)) return true;
            if (this.isFuelRowSlot(slot)) {
                Material bucket = Material.matchMaterial("BUCKET");
                return FureamFurnaceLogic.isFuel(stack) || bucket != null && stack.getType() == bucket;
            }
            if (this.isFunctionalAreaSlot(slot)) {
                int area = this.getCurrentFunctionalArea();
                return area < this.functionalAreas.size() &&
                    this.functionalAreas.get(area).isStorable(this, slot - 45);
            }
        }
        return false;
    }

    private static @NotNull ItemStack cloneOrAir(@Nullable ItemStack stack) {
        return ItemCompat.isEmpty(stack) ? new ItemStack(Material.AIR) : stack.clone();
    }

    private static boolean sameStack(@Nullable ItemStack left, @Nullable ItemStack right) {
        return ItemCompat.isEmpty(left) && ItemCompat.isEmpty(right) ||
            !ItemCompat.isEmpty(left) && !ItemCompat.isEmpty(right) &&
                left.getAmount() == right.getAmount() && left.isSimilar(right);
    }

    private static String safeFormat(String template, String fallback, Object... arguments) {
        String selected = template == null ? fallback : template;
        try {
            return String.format(selected, arguments);
        } catch (IllegalFormatException failure) {
            if (WARNED_FORMATS.add(selected) && FureamMain.getInstance() != null) {
                FureamMain.getInstance().getLogger().warning(
                    "Invalid GUI format string '" + selected + "'; using '" + fallback + "'"
                );
            }
            return String.format(fallback, arguments);
        }
    }

    static {
        FUNCTIONAL_AREA_REGISTRY.add(RecipeSettingAndXpFunctionalArea::new);
    }
}
