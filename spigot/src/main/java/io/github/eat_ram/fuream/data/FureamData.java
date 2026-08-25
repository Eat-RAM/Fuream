package io.github.eat_ram.fuream.data;

import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import org.jetbrains.annotations.NotNull;

public interface FureamData {
    void writeNbt(FureamDataHolder holder, @NotNull ReadWriteNBT nbt);

    void readNbt(FureamDataHolder holder, @NotNull ReadableNBT nbt);
}
