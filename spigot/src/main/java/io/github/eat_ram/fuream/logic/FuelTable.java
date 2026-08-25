package io.github.eat_ram.fuream.logic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class FuelTable {
    private static final Map<Material, Integer> FUEL_TIMES = new EnumMap<>(Material.class);

    static {
        loadDefaults();
        initDynamic();
    }

    public static void initDynamic() {
        try {
            // Dynamically synchronize with official server fuel registry if available
            Class<?> furnaceClass = Class.forName("net.minecraft.world.level.block.entity.TileEntityFurnace");
            Method getFuelMethod = null;
            for (Method m : furnaceClass.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && Map.class.isAssignableFrom(m.getReturnType()) && m.getParameterCount() == 0) {
                    getFuelMethod = m;
                    break;
                }
            }
            if (getFuelMethod != null) {
                getFuelMethod.setAccessible(true);
                Map<?, Integer> nmsFuelMap = (Map<?, Integer>) getFuelMethod.invoke(null);
                if (nmsFuelMap != null && !nmsFuelMap.isEmpty()) {
                    Class<?> craftItemClass = Class.forName("org.bukkit.craftbukkit.v1_20_R1.util.CraftMagicNumbers");
                    Method getMaterialMethod = craftItemClass.getMethod("getMaterial", Class.forName("net.minecraft.world.item.Item"));
                    for (Map.Entry<?, Integer> entry : nmsFuelMap.entrySet()) {
                        Material mat = (Material) getMaterialMethod.invoke(null, entry.getKey());
                        if (mat != null && entry.getValue() != null && entry.getValue() > 0) {
                            FUEL_TIMES.put(mat, entry.getValue());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Graceful fallback to default vanilla table
        }
    }

    private static void loadDefaults() {
        // High tier
        FUEL_TIMES.put(Material.LAVA_BUCKET, 20000);
        FUEL_TIMES.put(Material.COAL_BLOCK, 16000);
        FUEL_TIMES.put(Material.DRIED_KELP_BLOCK, 4000);
        FUEL_TIMES.put(Material.BLAZE_ROD, 2400);
        FUEL_TIMES.put(Material.COAL, 1600);
        FUEL_TIMES.put(Material.CHARCOAL, 1600);

        // Boats
        Material[] boats = {
            Material.OAK_BOAT, Material.SPRUCE_BOAT, Material.BIRCH_BOAT, Material.JUNGLE_BOAT,
            Material.ACACIA_BOAT, Material.DARK_OAK_BOAT, Material.MANGROVE_BOAT, Material.CHERRY_BOAT,
            Material.BAMBOO_RAFT,
            Material.OAK_CHEST_BOAT, Material.SPRUCE_CHEST_BOAT, Material.BIRCH_CHEST_BOAT, Material.JUNGLE_CHEST_BOAT,
            Material.ACACIA_CHEST_BOAT, Material.DARK_OAK_CHEST_BOAT, Material.MANGROVE_CHEST_BOAT, Material.CHERRY_CHEST_BOAT,
            Material.BAMBOO_CHEST_RAFT
        };
        for (Material m : boats) FUEL_TIMES.put(m, 1200);

        // Standard wood items (300 ticks)
        Material[] woods300 = {
            // Logs
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG,
            // Woods
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD, Material.JUNGLE_WOOD,
            Material.ACACIA_WOOD, Material.DARK_OAK_WOOD, Material.MANGROVE_WOOD, Material.CHERRY_WOOD,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD, Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD,
            // Planks
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS, Material.JUNGLE_PLANKS,
            Material.ACACIA_PLANKS, Material.DARK_OAK_PLANKS, Material.MANGROVE_PLANKS, Material.CHERRY_PLANKS,
            Material.BAMBOO_PLANKS, Material.BAMBOO_MOSAIC,
            // Stairs
            Material.OAK_STAIRS, Material.SPRUCE_STAIRS, Material.BIRCH_STAIRS, Material.JUNGLE_STAIRS,
            Material.ACACIA_STAIRS, Material.DARK_OAK_STAIRS, Material.MANGROVE_STAIRS, Material.CHERRY_STAIRS,
            Material.BAMBOO_STAIRS, Material.BAMBOO_MOSAIC_STAIRS,
            // Fences & Gates
            Material.OAK_FENCE, Material.SPRUCE_FENCE, Material.BIRCH_FENCE, Material.JUNGLE_FENCE,
            Material.ACACIA_FENCE, Material.DARK_OAK_FENCE, Material.MANGROVE_FENCE, Material.CHERRY_FENCE, Material.BAMBOO_FENCE,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE, Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE, Material.BAMBOO_FENCE_GATE,
            // Doors & Trapdoors
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR, Material.BAMBOO_TRAPDOOR,
            // Pressure plates & Buttons
            Material.OAK_PRESSURE_PLATE, Material.SPRUCE_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE, Material.DARK_OAK_PRESSURE_PLATE, Material.MANGROVE_PRESSURE_PLATE, Material.CHERRY_PRESSURE_PLATE,
            Material.BAMBOO_PRESSURE_PLATE,
            // Signs & Hanging Signs
            Material.OAK_SIGN, Material.SPRUCE_SIGN, Material.BIRCH_SIGN, Material.JUNGLE_SIGN,
            Material.ACACIA_SIGN, Material.DARK_OAK_SIGN, Material.MANGROVE_SIGN, Material.CHERRY_SIGN, Material.BAMBOO_SIGN,
            Material.OAK_HANGING_SIGN, Material.SPRUCE_HANGING_SIGN, Material.BIRCH_HANGING_SIGN, Material.JUNGLE_HANGING_SIGN,
            Material.ACACIA_HANGING_SIGN, Material.DARK_OAK_HANGING_SIGN, Material.MANGROVE_HANGING_SIGN, Material.CHERRY_HANGING_SIGN, Material.BAMBOO_HANGING_SIGN,
            // Blocks
            Material.CRAFTING_TABLE, Material.BOOKSHELF, Material.CHISELED_BOOKSHELF, Material.CHEST, Material.TRAPPED_CHEST,
            Material.BARREL, Material.DAYLIGHT_DETECTOR, Material.JUKEBOX, Material.NOTE_BLOCK, Material.COMPOSTER,
            Material.LOOM, Material.FLETCHING_TABLE, Material.CARTOGRAPHY_TABLE, Material.SMITHING_TABLE, Material.LECTERN,
            Material.BEEHIVE,
            // Banners
            Material.WHITE_BANNER, Material.ORANGE_BANNER, Material.MAGENTA_BANNER, Material.LIGHT_BLUE_BANNER,
            Material.YELLOW_BANNER, Material.LIME_BANNER, Material.PINK_BANNER, Material.GRAY_BANNER,
            Material.LIGHT_GRAY_BANNER, Material.CYAN_BANNER, Material.PURPLE_BANNER, Material.BLUE_BANNER,
            Material.BROWN_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.BLACK_BANNER,
            // Tools & Weapons
            Material.WOODEN_AXE, Material.WOODEN_HOE, Material.WOODEN_PICKAXE, Material.WOODEN_SHOVEL, Material.WOODEN_SWORD,
            Material.BOW, Material.CROSSBOW, Material.FISHING_ROD
        };
        for (Material m : woods300) FUEL_TIMES.put(m, 300);

        // Slabs (150 ticks)
        Material[] slabs150 = {
            Material.OAK_SLAB, Material.SPRUCE_SLAB, Material.BIRCH_SLAB, Material.JUNGLE_SLAB,
            Material.ACACIA_SLAB, Material.DARK_OAK_SLAB, Material.MANGROVE_SLAB, Material.CHERRY_SLAB,
            Material.BAMBOO_SLAB, Material.BAMBOO_MOSAIC_SLAB
        };
        for (Material m : slabs150) FUEL_TIMES.put(m, 150);

        // 200 ticks
        Material[] items200 = {
            Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON, Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON, Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON, Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON, Material.BOWL, Material.LADDER
        };
        for (Material m : items200) FUEL_TIMES.put(m, 200);

        // 100 ticks
        Material[] items100 = {
            Material.STICK, Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
            Material.CHERRY_SAPLING, Material.BAMBOO, Material.AZALEA, Material.FLOWERING_AZALEA
        };
        for (Material m : items100) FUEL_TIMES.put(m, 100);

        // 67 ticks (wool, carpet)
        Material[] items67 = {
            Material.WHITE_WOOL, Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.YELLOW_WOOL, Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL, Material.PURPLE_WOOL, Material.BLUE_WOOL,
            Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL, Material.BLACK_WOOL,
            Material.WHITE_CARPET, Material.ORANGE_CARPET, Material.MAGENTA_CARPET, Material.LIGHT_BLUE_CARPET,
            Material.YELLOW_CARPET, Material.LIME_CARPET, Material.PINK_CARPET, Material.GRAY_CARPET,
            Material.LIGHT_GRAY_CARPET, Material.CYAN_CARPET, Material.PURPLE_CARPET, Material.BLUE_CARPET,
            Material.BROWN_CARPET, Material.GREEN_CARPET, Material.RED_CARPET, Material.BLACK_CARPET
        };
        for (Material m : items67) FUEL_TIMES.put(m, 67);

        // Scaffolding (50 ticks)
        FUEL_TIMES.put(Material.SCAFFOLDING, 50);
    }

    public static boolean isFuel(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        return isFuel(stack.getType());
    }

    public static boolean isFuel(@Nullable Material material) {
        if (material == null || material.isAir()) return false;
        return FUEL_TIMES.containsKey(material);
    }

    public static int getFuelTime(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        return getFuelTime(stack.getType());
    }

    public static int getFuelTime(@Nullable Material material) {
        if (material == null || material.isAir()) return 0;
        return FUEL_TIMES.getOrDefault(material, 0);
    }

    private FuelTable() {
        throw new UnsupportedOperationException();
    }
}
