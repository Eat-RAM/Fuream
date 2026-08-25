package io.github.eat_ram.fuream;

import java.util.Locale;
import java.util.WeakHashMap;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import net.minecraft.block.BlastFurnaceBlock;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.SmokerBlock;
import net.minecraft.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.SmokerBlockEntity;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FureamMain {
    public static final String DATA_KEY = "FureamData";
    public static final WeakHashMap<
        @NotNull MinecraftServer, @NotNull FureamWorldConfig
    > WORLD_CONFIGS = new WeakHashMap<>();

    @Contract(value = "null -> null", pure = true)
    public static @Nullable FurnaceType getFurnaceType(@Nullable Object obj) {
        if (obj instanceof FurnaceType) {
            return (FurnaceType)obj;
        }
        if (obj instanceof String) {
            try {
                return FurnaceType.valueOf(
                    ((String)obj).toUpperCase(Locale.ROOT)
                );
            } catch (IllegalArgumentException ignored) {}
            return null;
        }
        if (obj instanceof FurnaceBlockEntity ||
            obj instanceof FurnaceBlock) {
            return FurnaceType.FURNACE;
        }
        if (obj instanceof SmokerBlockEntity || obj instanceof SmokerBlock) {
            return FurnaceType.SMOKER;
        }
        if (obj instanceof BlastFurnaceBlockEntity ||
            obj instanceof BlastFurnaceBlock) {
            return FurnaceType.BLAST_FURNACE;
        }
        return null;
    }
}
