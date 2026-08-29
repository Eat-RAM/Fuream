package io.github.eat_ram.fuream;

import io.github.eat_ram.fuream.api.FurnaceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

public class ConfigTest {
    @TempDir
    Path tempDir;

    @Test
    public void testDefaultConfig() {
        FureamWorldConfigImpl config = new FureamWorldConfigImpl();
        for (FurnaceType type : FurnaceType.values()) {
            assertEquals(9, config.getInputSlotCount().get(type));
            assertEquals(9, config.getFuelSlotCount().get(type));
            assertEquals(9, config.getOutputSlotCount().get(type));
            assertNotNull(config.getGuiTitle().get(type));
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

    @Test
    public void writerEmitsAllFieldsAndPreservesUnknownKeys() throws Exception {
        Path file = tempDir.resolve("fuream.json");
        Files.write(file, "{\"third_party_key\":42}".getBytes("UTF-8"));
        FureamWorldConfigImpl.writeWorldConfig(new FureamWorldConfigImpl(), file.toFile());

        JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), "UTF-8")).getAsJsonObject();
        assertEquals(42, root.get("third_party_key").getAsInt());
        assertTrue(root.has("gui_prev_recipe_item_id"));
        assertTrue(root.has("gui_xp_indicator_item_id"));
        assertTrue(root.has("gui_next_page_item_tooltip"));
        assertTrue(root.has("gui_title"));
        assertEquals(39, root.entrySet().size());
    }

    @Test
    public void localizedGuiTitleAcceptsSingleAndPerTypeValues() throws Exception {
        Path singleFile = tempDir.resolve("single.json");
        Files.write(singleFile, "{\"gui_title\":\"Localized Furnace\"}".getBytes("UTF-8"));
        FureamWorldConfigImpl single = new FureamWorldConfigImpl();
        FureamWorldConfigImpl.readWorldConfig(single, singleFile.toFile());
        for (FurnaceType type : FurnaceType.values()) {
            assertEquals("Localized Furnace", single.getGuiTitle().get(type));
        }

        Path mappedFile = tempDir.resolve("mapped.json");
        Files.write(mappedFile, "{\"gui_title\":{\"FURNACE\":\"Localized Oven\"}}".getBytes("UTF-8"));
        FureamWorldConfigImpl mapped = new FureamWorldConfigImpl();
        FureamWorldConfigImpl.readWorldConfig(mapped, mappedFile.toFile());
        assertEquals("Localized Oven", mapped.getGuiTitle().get(FurnaceType.FURNACE));
        assertEquals("Smoker", mapped.getGuiTitle().get(FurnaceType.SMOKER));
    }

    @Test
    public void writerRefusesToOverwriteMalformedExistingConfig() throws Exception {
        Path file = tempDir.resolve("broken.json");
        byte[] original = "{broken".getBytes("UTF-8");
        Files.write(file, original);

        assertThrows(Exception.class, () ->
            FureamWorldConfigImpl.writeWorldConfig(new FureamWorldConfigImpl(), file.toFile())
        );
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    public void oversizedLaneCountIsRejected() throws Exception {
        Path file = tempDir.resolve("oversized.json");
        Files.write(file, "{\"input_slot_count\":{\"FURNACE\":577}}".getBytes("UTF-8"));

        assertThrows(Exception.class, () ->
            FureamWorldConfigImpl.readWorldConfig(new FureamWorldConfigImpl(), file.toFile())
        );
    }
}
