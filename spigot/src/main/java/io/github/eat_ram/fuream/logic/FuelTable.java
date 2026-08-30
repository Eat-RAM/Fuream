package io.github.eat_ram.fuream.logic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import io.github.eat_ram.fuream.compat.ItemCompat;

public final class FuelTable {
    private static final Map<Material, Integer> FUEL_TIMES = new EnumMap<>(Material.class);

    static {
        loadDefaults();
        initDynamic();
    }

    public static void initDynamic() {
        try {
            Class<?> furnaceClass = getFurnaceNmsClass();
            if (furnaceClass == null) return;

            Class<?> craftMagicClass = getCraftMagicNumbersClass();
            if (craftMagicClass == null) return;

            Method getMaterialMethod = null;
            for (Method m : craftMagicClass.getMethods()) {
                if (m.getName().equals("getMaterial") && m.getParameterCount() == 1 && m.getReturnType() == Material.class) {
                    getMaterialMethod = m;
                    break;
                }
            }
            if (getMaterialMethod == null) return;

            Map<Material, Integer> best = new EnumMap<>(Material.class);
            for (Method candidate : furnaceClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(candidate.getModifiers()) || candidate.getParameterCount() != 0 ||
                    !Map.class.isAssignableFrom(candidate.getReturnType())) continue;
                try {
                    candidate.setAccessible(true);
                    Object value = candidate.invoke(null);
                    if (!(value instanceof Map)) continue;
                    Map<Material, Integer> converted = convertFuelMap((Map<?, ?>) value, getMaterialMethod);
                    if (converted.size() > best.size()) best = converted;
                } catch (Throwable ignored) {
                    // One inaccessible or unrelated map must not hide later candidates.
                }
            }
            if (!best.isEmpty()) FUEL_TIMES.putAll(best);
        } catch (Throwable ignored) {
            // Graceful fallback to default vanilla table
        }
    }

    private static Map<Material, Integer> convertFuelMap(Map<?, ?> source, Method getMaterialMethod) {
        Map<Material, Integer> converted = new EnumMap<>(Material.class);
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getValue() instanceof Number) || entry.getKey() == null) continue;
            int ticks = ((Number) entry.getValue()).intValue();
            if (ticks <= 0 || !getMaterialMethod.getParameterTypes()[0].isInstance(entry.getKey())) continue;
            try {
                Object material = getMaterialMethod.invoke(null, entry.getKey());
                if (material instanceof Material) converted.put((Material) material, ticks);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            }
        }
        return converted;
    }

    private static @Nullable Class<?> getFurnaceNmsClass() {
        String serverPkg = Bukkit.getServer() != null ? Bukkit.getServer().getClass().getPackage().getName() : "";
        String[] candidates = {
            "net.minecraft.world.level.block.entity.TileEntityFurnace",
            "net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity",
            serverPkg + ".block.entity.TileEntityFurnace",
            serverPkg.replace("org.bukkit.craftbukkit", "net.minecraft.server") + ".TileEntityFurnace"
        };
        for (String c : candidates) {
            try {
                return Class.forName(c);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static @Nullable Class<?> getCraftMagicNumbersClass() {
        String serverPkg = Bukkit.getServer() != null ? Bukkit.getServer().getClass().getPackage().getName() : "";
        String[] candidates = {
            serverPkg + ".util.CraftMagicNumbers",
            "org.bukkit.craftbukkit.util.CraftMagicNumbers"
        };
        for (String c : candidates) {
            try {
                return Class.forName(c);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static void safePut(String materialName, int ticks) {
        Material mat = Material.matchMaterial(materialName);
        if (mat != null) {
            FUEL_TIMES.put(mat, ticks);
        }
    }

    private static void safePutAll(String[] names, int ticks) {
        for (String n : names) {
            safePut(n, ticks);
        }
    }

    private static void loadDefaults() {
        // High tier
        safePut("LAVA_BUCKET", 20000);
        safePut("COAL_BLOCK", 16000);
        safePut("DRIED_KELP_BLOCK", 4000);
        safePut("BLAZE_ROD", 2400);
        safePut("COAL", 1600);
        safePut("CHARCOAL", 1600);

        // Boats & Rafts
        safePutAll(new String[]{
            "OAK_BOAT", "SPRUCE_BOAT", "BIRCH_BOAT", "JUNGLE_BOAT",
            "ACACIA_BOAT", "DARK_OAK_BOAT", "MANGROVE_BOAT", "CHERRY_BOAT",
            "BAMBOO_RAFT",
            "OAK_CHEST_BOAT", "SPRUCE_CHEST_BOAT", "BIRCH_CHEST_BOAT", "JUNGLE_CHEST_BOAT",
            "ACACIA_CHEST_BOAT", "DARK_OAK_CHEST_BOAT", "MANGROVE_CHEST_BOAT", "CHERRY_CHEST_BOAT",
            "BAMBOO_CHEST_RAFT"
        }, 1200);

        // Standard wood items (300 ticks)
        safePutAll(new String[]{
            // Logs
            "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "JUNGLE_LOG",
            "ACACIA_LOG", "DARK_OAK_LOG", "MANGROVE_LOG", "CHERRY_LOG",
            "STRIPPED_OAK_LOG", "STRIPPED_SPRUCE_LOG", "STRIPPED_BIRCH_LOG", "STRIPPED_JUNGLE_LOG",
            "STRIPPED_ACACIA_LOG", "STRIPPED_DARK_OAK_LOG", "STRIPPED_MANGROVE_LOG", "STRIPPED_CHERRY_LOG",
            // Woods
            "OAK_WOOD", "SPRUCE_WOOD", "BIRCH_WOOD", "JUNGLE_WOOD",
            "ACACIA_WOOD", "DARK_OAK_WOOD", "MANGROVE_WOOD", "CHERRY_WOOD",
            "STRIPPED_OAK_WOOD", "STRIPPED_SPRUCE_WOOD", "STRIPPED_BIRCH_WOOD", "STRIPPED_JUNGLE_WOOD",
            "STRIPPED_ACACIA_WOOD", "STRIPPED_DARK_OAK_WOOD", "STRIPPED_MANGROVE_WOOD", "STRIPPED_CHERRY_WOOD",
            // Planks
            "OAK_PLANKS", "SPRUCE_PLANKS", "BIRCH_PLANKS", "JUNGLE_PLANKS",
            "ACACIA_PLANKS", "DARK_OAK_PLANKS", "MANGROVE_PLANKS", "CHERRY_PLANKS",
            "BAMBOO_PLANKS", "BAMBOO_MOSAIC",
            // Stairs
            "OAK_STAIRS", "SPRUCE_STAIRS", "BIRCH_STAIRS", "JUNGLE_STAIRS",
            "ACACIA_STAIRS", "DARK_OAK_STAIRS", "MANGROVE_STAIRS", "CHERRY_STAIRS",
            "BAMBOO_STAIRS", "BAMBOO_MOSAIC_STAIRS",
            // Fences & Gates
            "OAK_FENCE", "SPRUCE_FENCE", "BIRCH_FENCE", "JUNGLE_FENCE",
            "ACACIA_FENCE", "DARK_OAK_FENCE", "MANGROVE_FENCE", "CHERRY_FENCE", "BAMBOO_FENCE",
            "OAK_FENCE_GATE", "SPRUCE_FENCE_GATE", "BIRCH_FENCE_GATE", "JUNGLE_FENCE_GATE",
            "ACACIA_FENCE_GATE", "DARK_OAK_FENCE_GATE", "MANGROVE_FENCE_GATE", "CHERRY_FENCE_GATE", "BAMBOO_FENCE_GATE",
            // Doors & Trapdoors
            "OAK_DOOR", "SPRUCE_DOOR", "BIRCH_DOOR", "JUNGLE_DOOR",
            "ACACIA_DOOR", "DARK_OAK_DOOR", "MANGROVE_DOOR", "CHERRY_DOOR", "BAMBOO_DOOR",
            "OAK_TRAPDOOR", "SPRUCE_TRAPDOOR", "BIRCH_TRAPDOOR", "JUNGLE_TRAPDOOR",
            "ACACIA_TRAPDOOR", "DARK_OAK_TRAPDOOR", "MANGROVE_TRAPDOOR", "CHERRY_TRAPDOOR", "BAMBOO_TRAPDOOR",
            // Pressure plates & Buttons
            "OAK_PRESSURE_PLATE", "SPRUCE_PRESSURE_PLATE", "BIRCH_PRESSURE_PLATE", "JUNGLE_PRESSURE_PLATE",
            "ACACIA_PRESSURE_PLATE", "DARK_OAK_PRESSURE_PLATE", "MANGROVE_PRESSURE_PLATE", "CHERRY_PRESSURE_PLATE",
            "BAMBOO_PRESSURE_PLATE",
            "OAK_BUTTON", "SPRUCE_BUTTON", "BIRCH_BUTTON", "JUNGLE_BUTTON",
            "ACACIA_BUTTON", "DARK_OAK_BUTTON", "MANGROVE_BUTTON", "CHERRY_BUTTON",
            "BAMBOO_BUTTON",
            // Signs & Hanging Signs
            "OAK_SIGN", "SPRUCE_SIGN", "BIRCH_SIGN", "JUNGLE_SIGN",
            "ACACIA_SIGN", "DARK_OAK_SIGN", "MANGROVE_SIGN", "CHERRY_SIGN", "BAMBOO_SIGN",
            "OAK_HANGING_SIGN", "SPRUCE_HANGING_SIGN", "BIRCH_HANGING_SIGN", "JUNGLE_HANGING_SIGN",
            "ACACIA_HANGING_SIGN", "DARK_OAK_HANGING_SIGN", "MANGROVE_HANGING_SIGN", "CHERRY_HANGING_SIGN", "BAMBOO_HANGING_SIGN",
            // Utility Blocks
            "CRAFTING_TABLE", "BOOKSHELF", "CHISELED_BOOKSHELF", "CHEST", "TRAPPED_CHEST",
            "BARREL", "DAYLIGHT_DETECTOR", "JUKEBOX", "NOTE_BLOCK", "COMPOSTER",
            "LOOM", "FLETCHING_TABLE", "CARTOGRAPHY_TABLE", "SMITHING_TABLE", "LECTERN",
            "BEEHIVE",
            // Banners
            "WHITE_BANNER", "ORANGE_BANNER", "MAGENTA_BANNER", "LIGHT_BLUE_BANNER",
            "YELLOW_BANNER", "LIME_BANNER", "PINK_BANNER", "GRAY_BANNER",
            "LIGHT_GRAY_BANNER", "CYAN_BANNER", "PURPLE_BANNER", "BLUE_BANNER",
            "BROWN_BANNER", "GREEN_BANNER", "RED_BANNER", "BLACK_BANNER",
            // Tools & Weapons
            "WOODEN_AXE", "WOODEN_HOE", "WOODEN_PICKAXE", "WOODEN_SHOVEL", "WOODEN_SWORD",
            "BOW", "CROSSBOW", "FISHING_ROD"
        }, 300);

        // Pre-flattening aliases used by 1.7-1.12.
        safePutAll(new String[]{
            "LOG", "LOG_2", "WOOD", "WOOD_STAIRS", "SPRUCE_WOOD_STAIRS",
            "BIRCH_WOOD_STAIRS", "JUNGLE_WOOD_STAIRS", "ACACIA_STAIRS",
            "DARK_OAK_STAIRS", "FENCE", "FENCE_GATE", "SPRUCE_FENCE",
            "BIRCH_FENCE", "JUNGLE_FENCE", "DARK_OAK_FENCE", "ACACIA_FENCE",
            "SPRUCE_FENCE_GATE", "BIRCH_FENCE_GATE", "JUNGLE_FENCE_GATE",
            "DARK_OAK_FENCE_GATE", "ACACIA_FENCE_GATE", "WOOD_DOOR",
            "TRAP_DOOR", "WOOD_PLATE", "WOOD_BUTTON", "SIGN", "WORKBENCH",
            "WOOD_AXE", "WOOD_HOE", "WOOD_PICKAXE", "WOOD_SPADE", "WOOD_SWORD"
        }, 300);

        // Slabs (150 ticks)
        safePutAll(new String[]{
            "OAK_SLAB", "SPRUCE_SLAB", "BIRCH_SLAB", "JUNGLE_SLAB",
            "ACACIA_SLAB", "DARK_OAK_SLAB", "MANGROVE_SLAB", "CHERRY_SLAB",
            "BAMBOO_SLAB", "BAMBOO_MOSAIC_SLAB"
        }, 150);
        safePut("WOOD_STEP", 150);

        // 200 ticks
        safePutAll(new String[]{"BOWL", "LADDER"}, 200);

        // 100 ticks
        safePutAll(new String[]{
            "STICK", "OAK_SAPLING", "SPRUCE_SAPLING", "BIRCH_SAPLING",
            "JUNGLE_SAPLING", "ACACIA_SAPLING", "DARK_OAK_SAPLING", "MANGROVE_PROPAGULE",
            "CHERRY_SAPLING", "BAMBOO", "AZALEA", "FLOWERING_AZALEA"
        }, 100);
        safePut("SAPLING", 100);

        // 67 ticks (wool, carpet)
        safePutAll(new String[]{
            "WHITE_WOOL", "ORANGE_WOOL", "MAGENTA_WOOL", "LIGHT_BLUE_WOOL",
            "YELLOW_WOOL", "LIME_WOOL", "PINK_WOOL", "GRAY_WOOL",
            "LIGHT_GRAY_WOOL", "CYAN_WOOL", "PURPLE_WOOL", "BLUE_WOOL",
            "BROWN_WOOL", "GREEN_WOOL", "RED_WOOL", "BLACK_WOOL",
            "WHITE_CARPET", "ORANGE_CARPET", "MAGENTA_CARPET", "LIGHT_BLUE_CARPET",
            "YELLOW_CARPET", "LIME_CARPET", "PINK_CARPET", "GRAY_CARPET",
            "LIGHT_GRAY_CARPET", "CYAN_CARPET", "PURPLE_CARPET", "BLUE_CARPET",
            "BROWN_CARPET", "GREEN_CARPET", "RED_CARPET", "BLACK_CARPET"
        }, 67);
        safePutAll(new String[]{"WOOL", "CARPET"}, 67);

        // Scaffolding (50 ticks)
        safePut("SCAFFOLDING", 50);
    }

    public static boolean isFuel(@Nullable ItemStack stack) {
        if (ItemCompat.isEmpty(stack)) return false;
        return isFuel(stack.getType());
    }

    public static boolean isFuel(@Nullable Material material) {
        if (ItemCompat.isAir(material)) return false;
        return FUEL_TIMES.containsKey(material);
    }

    public static int getFuelTime(@Nullable ItemStack stack) {
        if (ItemCompat.isEmpty(stack)) return 0;
        return getFuelTime(stack.getType());
    }

    public static int getFuelTime(@Nullable Material material) {
        if (ItemCompat.isAir(material)) return 0;
        return FUEL_TIMES.getOrDefault(material, 0);
    }

    private FuelTable() {
    }
}
