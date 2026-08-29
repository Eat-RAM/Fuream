package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

/** Optional Bukkit furnace events whose availability differs across legacy APIs. */
public final class FurnaceEventCompat {
    private static Constructor<?> extractConstructor;
    private static Method extractExperience;
    private static Constructor<?> startConstructor;
    private static Method startCookTime;

    public static void initialize() {
        initializeExtractEvent();
        initializeStartEvent();
    }

    public static void fireExtract(Player player, Block furnace, Material itemType, int amount) {
        if (amount <= 0 || ItemCompat.isAir(itemType) || extractConstructor == null) return;
        try {
            Event event = (Event) extractConstructor.newInstance(player, furnace, itemType, amount, 0);
            Bukkit.getPluginManager().callEvent(event);
            int experience = ((Number) extractExperience.invoke(event)).intValue();
            if (experience > 0) {
                ExperienceOrb orb = player.getWorld().spawn(player.getLocation(), ExperienceOrb.class);
                orb.setExperience(experience);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // FurnaceExtractEvent is absent on some of the oldest Bukkit APIs.
        }
    }

    public static int fireStartSmelt(
        Block block, ItemStack input, Object recipe, int fallbackCookTime
    ) {
        if (startConstructor == null || recipe == null) return fallbackCookTime;
        Class<?> recipeType = startConstructor.getParameterTypes()[2];
        if (!recipeType.isInstance(recipe)) return fallbackCookTime;
        try {
            Event event = (Event) startConstructor.newInstance(block, input.clone(), recipe);
            FurnaceEventDispatcher.call(event);
            if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                return Integer.MAX_VALUE;
            }
            return Math.max(1, ((Number) startCookTime.invoke(event)).intValue());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return fallbackCookTime;
        }
    }

    private static void initializeExtractEvent() {
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.inventory.FurnaceExtractEvent");
            extractConstructor = eventClass.getConstructor(
                Player.class, Block.class, Material.class, int.class, int.class
            );
            extractExperience = eventClass.getMethod("getExpToDrop");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            extractConstructor = null;
            extractExperience = null;
        }
    }

    private static void initializeStartEvent() {
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.inventory.FurnaceStartSmeltEvent");
            for (Constructor<?> constructor : eventClass.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 3 && Block.class.isAssignableFrom(types[0]) &&
                    ItemStack.class.isAssignableFrom(types[1])) {
                    startConstructor = constructor;
                    startCookTime = eventClass.getMethod("getTotalCookTime");
                    return;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        startConstructor = null;
        startCookTime = null;
    }

    private FurnaceEventCompat() {
    }
}
