package io.github.eat_ram.fuream.data;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

public interface FureamData {
    void writeNbt(FureamDataHolder holder, @NotNull NbtCompound nbt);

    void readNbt(FureamDataHolder holder, @NotNull NbtCompound nbt);
}
