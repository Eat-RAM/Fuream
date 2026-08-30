package io.github.eat_ram.fuream.data;

import org.jetbrains.annotations.Nullable;

/**
 * Interface implemented by furnace data holders. Gives the rest of the plugin
 * a type-safe way to reach the virtual smelting state of a furnace.
 */
@FunctionalInterface
public interface FureamDataHolder {
    <T extends FureamData> @Nullable T
    getFureamData(String key, Class<T> clazz);
}
