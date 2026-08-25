package io.github.eat_ram.fuream.data;

import org.jetbrains.annotations.Nullable;

/**
 * Mixed onto {@code AbstractFurnaceBlockEntity} and implemented by the
 * corresponding mixin. Gives the rest of the mod a type-safe way to reach
 * the virtual smelting state of a vanilla {@code FurnaceBlockEntity}
 * without leaking mixin internals.
 */
@FunctionalInterface
public interface FureamDataHolder {
    <T extends FureamData> @Nullable T
    getFureamData(String key, Class<T> clazz);
}
