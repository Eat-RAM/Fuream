package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

/** Optional Bukkit furnace events whose availability differs across legacy APIs. */
public final class FurnaceEventCompat {
    public static void fireExtract(Player player, Block furnace, Material itemType, int amount) {
        if (amount <= 0 || ItemCompat.isAir(itemType)) return;
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.inventory.FurnaceExtractEvent");
            Constructor<?> constructor = eventClass.getConstructor(
                Player.class, Block.class, Material.class, int.class, int.class
            );
            Event event = (Event) constructor.newInstance(player, furnace, itemType, amount, 0);
            Bukkit.getPluginManager().callEvent(event);
            Method getter = eventClass.getMethod("getExpToDrop");
            int experience = ((Number) getter.invoke(event)).intValue();
            if (experience > 0) {
                ExperienceOrb orb = player.getWorld().spawn(player.getLocation(), ExperienceOrb.class);
                orb.setExperience(experience);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // FurnaceExtractEvent is absent on some of the oldest Bukkit APIs.
        }
    }

    private FurnaceEventCompat() {
        throw new UnsupportedOperationException();
    }
}
