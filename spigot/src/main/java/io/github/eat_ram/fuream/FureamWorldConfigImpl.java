package io.github.eat_ram.fuream;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import java.io.File;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class FureamWorldConfigImpl implements FureamWorldConfig {
    public static void writeWorldConfig(
        @NotNull FureamWorldConfig config, @NotNull File configFile
    ) throws java.io.IOException {
        if (configFile.getParentFile() != null) {
            configFile.getParentFile().mkdirs();
        }
        JsonObject root = readExistingObject(configFile);

        putMap(root, "input_slot_count", config.getInputSlotCount());
        putMap(root, "fuel_slot_count", config.getFuelSlotCount());
        putMap(root, "output_slot_count", config.getOutputSlotCount());

        com.google.gson.JsonArray enabledTypes = new com.google.gson.JsonArray();
        for (FurnaceType type : config.getEnabledFurnaceTypes()) {
            enabledTypes.add(type.name());
        }
        root.add("enabled_furnace_types", enabledTypes);

        com.google.gson.JsonArray preventsHopper = new com.google.gson.JsonArray();
        for (FurnaceType type : config.getPreventsHopperInsertNonSmeltable()) {
            preventsHopper.add(type.name());
        }
        root.add("prevents_hopper_insert_non_smeltable", preventsHopper);

        putMap(root, "gui_border_item_id", config.getGuiBorderItemId());
        putMap(root, "gui_fuel_left_item_id", config.getGuiFuelLeftItemId());
        putMap(root, "gui_fuel_used_item_id", config.getGuiFuelUsedItemId());
        putMap(root, "gui_progress_done_item_id", config.getGuiProgressDoneItemId());
        putMap(root, "gui_progress_remaining_item_id", config.getGuiProgressRemainingItemId());
        putMap(root, "gui_prev_recipe_item_id", config.getGuiPrevRecipeItemId());
        putMap(root, "gui_next_recipe_item_id", config.getGuiNextRecipeItemId());
        putMap(root, "gui_xp_indicator_item_id", config.getGuiXpIndicatorItemId());
        putMap(root, "gui_next_functional_area_item_id", config.getGuiNextFunctionalAreaItemId());
        putMap(root, "gui_prev_page_item_id", config.getGuiPrevPageItemId());
        putMap(root, "gui_next_page_item_id", config.getGuiNextPageItemId());

        putMap(root, "gui_title", config.getGuiTitle());
        putMap(root, "gui_border_item_title", config.getGuiBorderItemTitle());
        putMap(root, "gui_fuel_item_title", config.getGuiFuelItemTitle());
        putMap(root, "gui_fuel_item_tooltip", config.getGuiFuelItemTooltip());
        putMap(root, "gui_progress_item_title", config.getGuiProgressItemTitle());
        putMap(root, "gui_progress_item_tooltip", config.getGuiProgressItemTooltip());
        putMap(root, "gui_prev_recipe_item_title", config.getGuiPrevRecipeItemTitle());
        putMap(root, "gui_prev_recipe_empty_item_tooltip", config.getGuiPrevRecipeEmptyItemTooltip());
        putMap(root, "gui_prev_recipe_arbitrary_item_tooltip", config.getGuiPrevRecipeArbitraryItemTooltip());
        putMap(root, "gui_prev_recipe_item_tooltip", config.getGuiPrevRecipeItemTooltip());
        putMap(root, "gui_next_recipe_item_title", config.getGuiNextRecipeItemTitle());
        putMap(root, "gui_next_recipe_empty_item_tooltip", config.getGuiNextRecipeEmptyItemTooltip());
        putMap(root, "gui_next_recipe_arbitrary_item_tooltip", config.getGuiNextRecipeArbitraryItemTooltip());
        putMap(root, "gui_next_recipe_item_tooltip", config.getGuiNextRecipeItemTooltip());
        putMap(root, "gui_xp_indicator_item_title", config.getGuiXpIndicatorItemTitle());
        putMap(root, "gui_xp_indicator_gainable_item_title", config.getGuiXpIndicatorGainableItemTitle());
        putMap(root, "gui_xp_indicator_item_tooltip", config.getGuiXpIndicatorItemTooltip());
        putMap(root, "gui_next_functional_area_item_title", config.getGuiNextFunctionalAreaItemTitle());
        putMap(root, "gui_prev_page_item_title", config.getGuiPrevPageItemTitle());
        putMap(root, "gui_prev_page_item_tooltip", config.getGuiPrevPageItemTooltip());
        putMap(root, "gui_next_page_item_title", config.getGuiNextPageItemTitle());
        putMap(root, "gui_next_page_item_tooltip", config.getGuiNextPageItemTooltip());

        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (java.io.FileWriter fw = new java.io.FileWriter(configFile)) {
            gson.toJson(root, fw);
        }
    }

    private static JsonObject readExistingObject(File file) {
        if (!file.isFile()) return new JsonObject();
        try (FileReader reader = new FileReader(file)) {
            JsonElement element = new JsonParser().parse(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    private static void putMap(JsonObject root, String key, Map<FurnaceType, ?> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<FurnaceType, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number) object.addProperty(entry.getKey().name(), (Number) value);
            else if (value instanceof Boolean) object.addProperty(entry.getKey().name(), (Boolean) value);
            else object.addProperty(entry.getKey().name(), String.valueOf(value));
        }
        root.add(key, object);
    }

    private static void readStringMap(
        JsonObject root, String key, EnumMap<FurnaceType, String> values
    ) {
        if (!root.has(key)) return;
        JsonElement element = root.get(key);
        if (element.isJsonPrimitive()) {
            String value = element.getAsString();
            for (FurnaceType type : FurnaceType.values()) values.put(type, value);
            return;
        }
        if (!element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            try {
                values.put(FurnaceType.valueOf(entry.getKey()), entry.getValue().getAsString());
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void readWorldConfig(
        @NotNull FureamWorldConfig config, @NotNull File configFile
    ) throws FileNotFoundException, JsonIOException, JsonSyntaxException {
        final JsonElement JSON;
        try (final FileReader FR = new FileReader(configFile)) {
            JSON = new JsonParser().parse(FR);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new JsonIOException(e);
        }
        if (JSON.isJsonObject()) {
            JsonObject obj = JSON.getAsJsonObject();
            readStringMap(obj, "gui_title", config.getGuiTitle());
            if (obj.has("input_slot_count")) {
                JsonElement ele = obj.get("input_slot_count");
                if (ele.isJsonObject()) {
                    EnumMap<FurnaceType, Integer> inputSlotCount =
                    config.getInputSlotCount();
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                int count = value.getAsInt();
                                if (count > 0) {
                                    inputSlotCount.put(
                                        FurnaceType.valueOf(i.getKey()), count
                                    );
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            if (obj.has("fuel_slot_count")) {
                JsonElement ele = obj.get("fuel_slot_count");
                if (ele.isJsonObject()) {
                    EnumMap<FurnaceType, Integer> fuelSlotCount =
                    config.getFuelSlotCount();
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                int count = value.getAsInt();
                                if (count > 0) {
                                    fuelSlotCount.put(
                                        FurnaceType.valueOf(i.getKey()), count
                                    );
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            if (obj.has("output_slot_count")) {
                JsonElement ele = obj.get("output_slot_count");
                if (ele.isJsonObject()) {
                    EnumMap<FurnaceType, Integer> outputSlotCount =
                    config.getOutputSlotCount();
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                int count = value.getAsInt();
                                if (count > 0) {
                                    outputSlotCount.put(
                                        FurnaceType.valueOf(i.getKey()), count
                                    );
                                }
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            if (obj.has("prevents_hopper_insert_non_smeltable")) {
                JsonElement ele =
                obj.get("prevents_hopper_insert_non_smeltable");
                if (ele.isJsonArray()) {
                    EnumSet<FurnaceType> preventsHopperInsertNonSmeltable =
                    config.getPreventsHopperInsertNonSmeltable();
                    for (JsonElement i : ele.getAsJsonArray()) {
                        if (i.isJsonPrimitive()) {
                            try {
                                preventsHopperInsertNonSmeltable
                                .add(FurnaceType.valueOf(i.getAsString()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            if (obj.has("enabled_furnace_types")) {
                JsonElement ele = obj.get("enabled_furnace_types");
                if (ele.isJsonArray()) {
                    EnumSet<FurnaceType> enabledFurnaceTypes =
                    config.getEnabledFurnaceTypes();
                    for (JsonElement i : ele.getAsJsonArray()) {
                        if (i.isJsonPrimitive()) {
                            try {
                                enabledFurnaceTypes
                                .add(FurnaceType.valueOf(i.getAsString()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
            // Below are generated by a Python script
            if (obj.has("gui_border_item_id")) {
                JsonElement ele = obj.get("gui_border_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiBorderItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_fuel_left_item_id")) {
                JsonElement ele = obj.get("gui_fuel_left_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiFuelLeftItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_fuel_used_item_id")) {
                JsonElement ele = obj.get("gui_fuel_used_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiFuelUsedItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_progress_done_item_id")) {
                JsonElement ele = obj.get("gui_progress_done_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiProgressDoneItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_progress_remaining_item_id")) {
                JsonElement ele = obj.get("gui_progress_remaining_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiProgressRemainingItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_recipe_item_id")) {
                JsonElement ele = obj.get("gui_prev_recipe_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevRecipeItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_recipe_item_id")) {
                JsonElement ele = obj.get("gui_next_recipe_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextRecipeItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_xp_indicator_item_id")) {
                JsonElement ele = obj.get("gui_xp_indicator_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiXpIndicatorItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_functional_area_item_id")) {
                JsonElement ele = obj.get("gui_next_functional_area_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextFunctionalAreaItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_page_item_id")) {
                JsonElement ele = obj.get("gui_prev_page_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevPageItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_page_item_id")) {
                JsonElement ele = obj.get("gui_next_page_item_id");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextPageItemId();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_border_item_title")) {
                JsonElement ele = obj.get("gui_border_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiBorderItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_fuel_item_title")) {
                JsonElement ele = obj.get("gui_fuel_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiFuelItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_fuel_item_tooltip")) {
                JsonElement ele = obj.get("gui_fuel_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiFuelItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_progress_item_title")) {
                JsonElement ele = obj.get("gui_progress_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiProgressItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_progress_item_tooltip")) {
                JsonElement ele = obj.get("gui_progress_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiProgressItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_recipe_item_title")) {
                JsonElement ele = obj.get("gui_prev_recipe_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevRecipeItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_recipe_empty_item_tooltip")) {
                JsonElement ele = obj.get("gui_prev_recipe_empty_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevRecipeEmptyItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_recipe_arbitrary_item_tooltip")) {
                JsonElement ele = obj.get("gui_prev_recipe_arbitrary_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevRecipeArbitraryItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_recipe_item_tooltip")) {
                JsonElement ele = obj.get("gui_prev_recipe_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevRecipeItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_recipe_item_title")) {
                JsonElement ele = obj.get("gui_next_recipe_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextRecipeItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_recipe_empty_item_tooltip")) {
                JsonElement ele = obj.get("gui_next_recipe_empty_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextRecipeEmptyItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_recipe_arbitrary_item_tooltip")) {
                JsonElement ele = obj.get("gui_next_recipe_arbitrary_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextRecipeArbitraryItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_recipe_item_tooltip")) {
                JsonElement ele = obj.get("gui_next_recipe_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextRecipeItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_xp_indicator_item_title")) {
                JsonElement ele = obj.get("gui_xp_indicator_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiXpIndicatorItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_xp_indicator_gainable_item_title")) {
                JsonElement ele = obj.get("gui_xp_indicator_gainable_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiXpIndicatorGainableItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_xp_indicator_item_tooltip")) {
                JsonElement ele = obj.get("gui_xp_indicator_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiXpIndicatorItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_functional_area_item_title")) {
                JsonElement ele =
                obj.get("gui_next_functional_area_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextFunctionalAreaItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_page_item_title")) {
                JsonElement ele = obj.get("gui_prev_page_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevPageItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_prev_page_item_tooltip")) {
                JsonElement ele = obj.get("gui_prev_page_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiPrevPageItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_page_item_title")) {
                JsonElement ele = obj.get("gui_next_page_item_title");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextPageItemTitle();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
            if (obj.has("gui_next_page_item_tooltip")) {
                JsonElement ele = obj.get("gui_next_page_item_tooltip");
                EnumMap<FurnaceType, String> ids =
                config.getGuiNextPageItemTooltip();
                if (ele.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> i :
                        ele.getAsJsonObject().entrySet()) {
                        JsonElement value = i.getValue();
                        if (value.isJsonPrimitive()) {
                            try {
                                ids.put(
                                    FurnaceType.valueOf(i.getKey()),
                                    value.getAsString()
                                );
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                } else if (ele.isJsonPrimitive()) {
                    String id = ele.getAsString();
                    for (FurnaceType type : FurnaceType.values()) {
                        ids.put(type, id);
                    }
                }
            }
        }
    }

    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    inputSlotCount = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    fuelSlotCount = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    outputSlotCount = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumSet<FurnaceType> preventsHopperInsertNonSmeltable =
    EnumSet.noneOf(FurnaceType.class);
    private @NotNull EnumSet<FurnaceType> enabledFurnaceTypes =
    EnumSet.noneOf(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiBorderItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiFuelLeftItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiFuelUsedItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiProgressDoneItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiProgressRemainingItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevRecipeItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextRecipeItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiXpIndicatorItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextFunctionalAreaItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevPageItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextPageItemId = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiBorderItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiFuelItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiFuelItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiProgressItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiProgressItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevRecipeItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevRecipeEmptyItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevRecipeArbitraryItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevRecipeItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextRecipeItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextRecipeEmptyItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextRecipeArbitraryItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextRecipeItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiXpIndicatorItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiXpIndicatorGainableItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiXpIndicatorItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextFunctionalAreaItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevPageItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiPrevPageItemTooltip = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextPageItemTitle = new EnumMap<>(FurnaceType.class);
    private @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    guiNextPageItemTooltip = new EnumMap<>(FurnaceType.class);

    public FureamWorldConfigImpl() {
        for (FurnaceType type : FurnaceType.values()) {
            this.inputSlotCount.put(type, 9);
            this.fuelSlotCount.put(type, 9);
            this.outputSlotCount.put(type, 9);
            this.guiBorderItemId.put(
                type, "minecraft:black_stained_glass_pane"
            );
            this.guiFuelLeftItemId.put(
                type, "minecraft:orange_stained_glass_pane"
            );
            this.guiFuelUsedItemId.put(
                type, "minecraft:black_stained_glass_pane"
            );
            this.guiProgressDoneItemId.put(
                type, "minecraft:white_stained_glass_pane"
            );
            this.guiProgressRemainingItemId.put(
                type, "minecraft:black_stained_glass_pane"
            );
            this.guiPrevRecipeItemId
            .put(type, "minecraft:book");
            this.guiNextRecipeItemId
            .put(type, "minecraft:book");
            this.guiXpIndicatorItemId.put(
                type, "minecraft:lime_stained_glass_pane"
            );
            this.guiNextFunctionalAreaItemId
            .put(type, "minecraft:command_block");
            this.guiPrevPageItemId
            .put(type, "minecraft:arrow");
            this.guiNextPageItemId
            .put(type, "minecraft:arrow");
            this.guiTitle.put(type, type == FurnaceType.SMOKER ? "Smoker" :
                (type == FurnaceType.BLAST_FURNACE ? "Blast Furnace" : "Furnace"));
            this.guiBorderItemTitle.put(type, "GUI Border");
            this.guiFuelItemTitle.put(type, "Fuel");
            this.guiFuelItemTooltip.put(type, "Available: %d / %d");
            this.guiProgressItemTitle.put(type, "Progress");
            this.guiProgressItemTooltip.put(type, "%d / %d");
            this.guiPrevRecipeItemTitle.put(type, "Previous Recipe");
            this.guiPrevRecipeEmptyItemTooltip.put(
                type,
                "Please insert an item into the slot on the left to adjust"
            );
            this.guiPrevRecipeArbitraryItemTooltip
            .put(type, "Current: arbitrary of %d");
            this.guiPrevRecipeItemTooltip.put(type, "Current: %d / %d");
            this.guiNextRecipeItemTitle.put(type, "Next Recipe");
            this.guiNextRecipeEmptyItemTooltip.put(
                type,
                "Please insert an item into the slot on the left to adjust"
            );
            this.guiNextRecipeArbitraryItemTooltip
            .put(type, "Current: arbitrary of %d");
            this.guiNextRecipeItemTooltip.put(type, "Current: %d / %d");
            this.guiXpIndicatorItemTitle.put(type, "XP");
            this.guiXpIndicatorGainableItemTitle
            .put(type, "XP (click to gain)");
            this.guiXpIndicatorItemTooltip.put(type, "Current: %.1f");
            this.guiNextFunctionalAreaItemTitle
            .put(type, "Next Functional Area");
            this.guiPrevPageItemTitle.put(type, "Previous Page");
            this.guiPrevPageItemTooltip.put(type, "Page %d");
            this.guiNextPageItemTitle.put(type, "Next Page");
            this.guiNextPageItemTooltip.put(type, "Page %d");
        }
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getInputSlotCount() {
        return this.inputSlotCount;
    }

    @Override
    public void setInputSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    ) {
        this.inputSlotCount = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getFuelSlotCount() {
        return this.fuelSlotCount;
    }

    @Override
    public void setFuelSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    ) {
        this.fuelSlotCount = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer>
    getOutputSlotCount() {
        return this.outputSlotCount;
    }

    @Override
    public void setOutputSlotCount(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull Integer> map
    ) {
        this.outputSlotCount = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumSet<FurnaceType>
    getPreventsHopperInsertNonSmeltable() {
        return this.preventsHopperInsertNonSmeltable;
    }

    @Override
    public void setPreventsHopperInsertNonSmeltable(
        @NotNull EnumSet<FurnaceType> types
    ) {
        this.preventsHopperInsertNonSmeltable = types;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumSet<FurnaceType> getEnabledFurnaceTypes() {
        return this.enabledFurnaceTypes;
    }

    @Override
    public void
    setEnabledFurnaceTypes(@NotNull EnumSet<FurnaceType> types) {
        this.enabledFurnaceTypes = types;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiBorderItemId() {
        return this.guiBorderItemId;
    }

    @Override
    public void setGuiBorderItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiBorderItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelLeftItemId() {
        return this.guiFuelLeftItemId;
    }

    @Override
    public void setGuiFuelLeftItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiFuelLeftItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelUsedItemId() {
        return this.guiFuelUsedItemId;
    }

    @Override
    public void setGuiFuelUsedItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiFuelUsedItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressDoneItemId() {
        return this.guiProgressDoneItemId;
    }

    @Override
    public void setGuiProgressDoneItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiProgressDoneItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressRemainingItemId() {
        return this.guiProgressRemainingItemId;
    }

    @Override
    public void setGuiProgressRemainingItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiProgressRemainingItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemId() {
        return this.guiPrevRecipeItemId;
    }

    @Override
    public void setGuiPrevRecipeItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevRecipeItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemId() {
        return this.guiNextRecipeItemId;
    }

    @Override
    public void setGuiNextRecipeItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextRecipeItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemId() {
        return this.guiXpIndicatorItemId;
    }

    @Override
    public void setGuiXpIndicatorItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiXpIndicatorItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextFunctionalAreaItemId() {
        return this.guiNextFunctionalAreaItemId;
    }

    @Override
    public void setGuiNextFunctionalAreaItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextFunctionalAreaItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemId() {
        return this.guiPrevPageItemId;
    }

    @Override
    public void setGuiPrevPageItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevPageItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemId() {
        return this.guiNextPageItemId;
    }

    @Override
    public void setGuiNextPageItemId(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextPageItemId = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiTitle() {
        return this.guiTitle;
    }

    @Override
    public void setGuiTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiBorderItemTitle() {
        return this.guiBorderItemTitle;
    }

    @Override
    public void setGuiBorderItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiBorderItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelItemTitle() {
        return this.guiFuelItemTitle;
    }

    @Override
    public void setGuiFuelItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiFuelItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiFuelItemTooltip() {
        return this.guiFuelItemTooltip;
    }

    @Override
    public void setGuiFuelItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiFuelItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressItemTitle() {
        return this.guiProgressItemTitle;
    }

    @Override
    public void setGuiProgressItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiProgressItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiProgressItemTooltip() {
        return this.guiProgressItemTooltip;
    }

    @Override
    public void setGuiProgressItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiProgressItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemTitle() {
        return this.guiPrevRecipeItemTitle;
    }

    @Override
    public void setGuiPrevRecipeItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevRecipeItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeEmptyItemTooltip() {
        return this.guiPrevRecipeEmptyItemTooltip;
    }

    @Override
    public void setGuiPrevRecipeEmptyItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevRecipeEmptyItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeArbitraryItemTooltip() {
        return this.guiPrevRecipeArbitraryItemTooltip;
    }

    @Override
    public void setGuiPrevRecipeArbitraryItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevRecipeArbitraryItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevRecipeItemTooltip() {
        return this.guiPrevRecipeItemTooltip;
    }

    @Override
    public void setGuiPrevRecipeItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevRecipeItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemTitle() {
        return this.guiNextRecipeItemTitle;
    }

    @Override
    public void setGuiNextRecipeItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextRecipeItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeEmptyItemTooltip() {
        return this.guiNextRecipeEmptyItemTooltip;
    }

    @Override
    public void setGuiNextRecipeEmptyItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextRecipeEmptyItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeArbitraryItemTooltip() {
        return this.guiNextRecipeArbitraryItemTooltip;
    }

    @Override
    public void setGuiNextRecipeArbitraryItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextRecipeArbitraryItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextRecipeItemTooltip() {
        return this.guiNextRecipeItemTooltip;
    }

    @Override
    public void setGuiNextRecipeItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextRecipeItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemTitle() {
        return this.guiXpIndicatorItemTitle;
    }

    @Override
    public void setGuiXpIndicatorItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiXpIndicatorItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorGainableItemTitle() {
        return this.guiXpIndicatorGainableItemTitle;
    }

    @Override
    public void setGuiXpIndicatorGainableItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiXpIndicatorGainableItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiXpIndicatorItemTooltip() {
        return this.guiXpIndicatorItemTooltip;
    }

    @Override
    public void setGuiXpIndicatorItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiXpIndicatorItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextFunctionalAreaItemTitle() {
        return this.guiNextFunctionalAreaItemTitle;
    }

    @Override
    public void setGuiNextFunctionalAreaItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextFunctionalAreaItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemTitle() {
        return this.guiPrevPageItemTitle;
    }

    @Override
    public void setGuiPrevPageItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevPageItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiPrevPageItemTooltip() {
        return this.guiPrevPageItemTooltip;
    }

    @Override
    public void setGuiPrevPageItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiPrevPageItemTooltip = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemTitle() {
        return this.guiNextPageItemTitle;
    }

    @Override
    public void setGuiNextPageItemTitle(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextPageItemTitle = map;
    }

    @Override
    @Contract(pure = true)
    public @NotNull EnumMap<@NotNull FurnaceType, @NotNull String>
    getGuiNextPageItemTooltip() {
        return this.guiNextPageItemTooltip;
    }

    @Override
    public void setGuiNextPageItemTooltip(
        @NotNull EnumMap<@NotNull FurnaceType, @NotNull String> map
    ) {
        this.guiNextPageItemTooltip = map;
    }
}
