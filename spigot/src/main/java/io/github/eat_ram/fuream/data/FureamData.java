package io.github.eat_ram.fuream.data;

import io.github.eat_ram.fuream.nbt.NativeNbtCompound;
import org.jetbrains.annotations.NotNull;

public interface FureamData {
    void writeNbt(FureamDataHolder holder, @NotNull NativeNbtCompound nbt);

    void readNbt(FureamDataHolder holder, @NotNull NativeNbtCompound nbt);
}
