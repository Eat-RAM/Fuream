package io.github.eat_ram.fuream.compat;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

/** Distinguishes Fuream's synthetic furnace events from the vanilla furnace engine. */
public final class FurnaceEventDispatcher {
    private static final ThreadLocal<Integer> DISPATCH_DEPTH = new ThreadLocal<>();

    public static void call(@NotNull Event event) {
        Integer current = DISPATCH_DEPTH.get();
        DISPATCH_DEPTH.set(current == null ? 1 : current + 1);
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            int next = DISPATCH_DEPTH.get() - 1;
            if (next == 0) {
                DISPATCH_DEPTH.remove();
            } else {
                DISPATCH_DEPTH.set(next);
            }
        }
    }

    public static boolean isDispatching() {
        Integer depth = DISPATCH_DEPTH.get();
        return depth != null && depth > 0;
    }

    private FurnaceEventDispatcher() {
    }
}
