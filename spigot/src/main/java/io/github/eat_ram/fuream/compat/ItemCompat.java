package io.github.eat_ram.fuream.compat;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemCompat {
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getAmount() <= 0 || isAir(stack.getType());
    }

    public static boolean isAir(Material material) {
        if (material == null) return true;
        String name = material.name();
        return "AIR".equals(name) || name.endsWith("_AIR");
    }

    public static ItemStack empty() {
        return new ItemStack(Material.AIR);
    }

    public static Material matchMaterial(String configured, Material fallback) {
        if (configured == null || configured.trim().isEmpty()) return fallback;
        String name = configured.trim();
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        name = name.toUpperCase(Locale.ROOT);
        Material result = Material.matchMaterial(name);
        if (result != null) return result;

        // GUI aliases for the pre-flattening material registry.
        if (name.endsWith("_STAINED_GLASS_PANE")) {
            result = Material.matchMaterial("STAINED_GLASS_PANE");
        }
        return result != null ? result : fallback;
    }

    private ItemCompat() {
    }
}
