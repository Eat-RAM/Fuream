package io.github.eat_ram.fuream;

import io.github.eat_ram.fuream.api.FurnaceType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {
    @Test
    public void testDefaultConfig() {
        FureamWorldConfigImpl config = new FureamWorldConfigImpl();
        for (FurnaceType type : FurnaceType.values()) {
            assertEquals(9, config.getInputSlotCount().get(type));
            assertEquals(9, config.getFuelSlotCount().get(type));
            assertEquals(9, config.getOutputSlotCount().get(type));
            assertEquals("GUI Border", config.getGuiBorderItemTitle().get(type));
            assertEquals("Fuel", config.getGuiFuelItemTitle().get(type));
            assertEquals("Progress", config.getGuiProgressItemTitle().get(type));
            assertEquals("Previous Recipe", config.getGuiPrevRecipeItemTitle().get(type));
            assertEquals("Next Recipe", config.getGuiNextRecipeItemTitle().get(type));
            assertEquals("XP", config.getGuiXpIndicatorItemTitle().get(type));
            assertEquals("XP (click to gain)", config.getGuiXpIndicatorGainableItemTitle().get(type));
            assertEquals("Next Functional Area", config.getGuiNextFunctionalAreaItemTitle().get(type));
            assertEquals("Previous Page", config.getGuiPrevPageItemTitle().get(type));
            assertEquals("Next Page", config.getGuiNextPageItemTitle().get(type));
        }
    }
}
