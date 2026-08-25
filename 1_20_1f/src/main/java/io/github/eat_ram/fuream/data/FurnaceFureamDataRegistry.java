package io.github.eat_ram.fuream.data;

import java.util.HashMap;
import java.util.function.Function;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public abstract class FurnaceFureamDataRegistry {
    public static final @NotNull HashMap<@NotNull String, @NotNull Function<
        ? super AbstractFurnaceBlockEntity, ? extends @NotNull FureamData
    >> REGISTRY = new HashMap<>();

    @Contract("-> fail")
    private FurnaceFureamDataRegistry() {
        throw new UnsupportedOperationException();
    }

    static {
        FurnaceFureamDataRegistry.REGISTRY
        .put("fuream", be -> new FureamFurnaceData());
    }
}
