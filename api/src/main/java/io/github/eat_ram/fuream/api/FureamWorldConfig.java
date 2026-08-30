package io.github.eat_ram.fuream.api;

import java.util.EnumMap;
import java.util.EnumSet;

import org.jetbrains.annotations.NotNull;

public interface FureamWorldConfig {
    // Game logic

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getInputSlotCount();

    void setInputSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getFuelSlotCount();

    void setFuelSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getOutputSlotCount();

    void setOutputSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    );

    @NotNull EnumSet<FurnaceType> getPreventsHopperInsertNonSmeltable();

    void setPreventsHopperInsertNonSmeltable(
        @NotNull EnumSet<FurnaceType> types
    );

    @NotNull EnumSet<FurnaceType> getEnabledFurnaceTypes();

    void setEnabledFurnaceTypes(@NotNull EnumSet<FurnaceType> types);

    // Appearance

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiBorderItemId();

    void setGuiBorderItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelLeftItemId();

    void setGuiFuelLeftItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelUsedItemId();

    void setGuiFuelUsedItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressDoneItemId();

    void setGuiProgressDoneItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressRemainingItemId();

    void setGuiProgressRemainingItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemId();

    void setGuiPrevRecipeItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemId();

    void setGuiNextRecipeItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemId();

    void setGuiXpIndicatorItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextFunctionalAreaItemId();

    void setGuiNextFunctionalAreaItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemId();

    void setGuiPrevPageItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemId();

    void setGuiNextPageItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    // i18n

    default @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> getGuiTitle() {
        EnumMap<FurnaceType, String> titles = new EnumMap<>(FurnaceType.class);
        titles.put(FurnaceType.FURNACE, "Furnace");
        titles.put(FurnaceType.SMOKER, "Smoker");
        titles.put(FurnaceType.BLAST_FURNACE, "Blast Furnace");
        return titles;
    }

    default void setGuiTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
    }

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiBorderItemTitle();

    void setGuiBorderItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelItemTitle();

    void setGuiFuelItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelItemTooltip();

    void setGuiFuelItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressItemTitle();

    void setGuiProgressItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressItemTooltip();

    void setGuiProgressItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemTitle();

    void setGuiPrevRecipeItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeEmptyItemTooltip();

    void setGuiPrevRecipeEmptyItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeArbitraryItemTooltip();

    void setGuiPrevRecipeArbitraryItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemTooltip();

    void setGuiPrevRecipeItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemTitle();

    void setGuiNextRecipeItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeEmptyItemTooltip();

    void setGuiNextRecipeEmptyItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );


    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeArbitraryItemTooltip();

    void setGuiNextRecipeArbitraryItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemTooltip();

    void setGuiNextRecipeItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemTitle();

    void setGuiXpIndicatorItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorGainableItemTitle();

    void setGuiXpIndicatorGainableItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemTooltip();

    void setGuiXpIndicatorItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextFunctionalAreaItemTitle();

    void setGuiNextFunctionalAreaItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemTitle();

    void setGuiPrevPageItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemTooltip();

    void setGuiPrevPageItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemTitle();

    void setGuiNextPageItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );

    @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemTooltip();

    void setGuiNextPageItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    );
}
