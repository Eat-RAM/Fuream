package io.github.eat_ram.fuream.util;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

public final class FurnacePos {
    public final UUID worldId;
    public final @NotNull String worldName;
    public final int x;
    public final int y;
    public final int z;

    public FurnacePos(@NotNull String worldName, int x, int y, int z) {
        this(null, worldName, x, y, z);
    }

    public FurnacePos(UUID worldId, @NotNull String worldName, int x, int y, int z) {
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public FurnacePos(@NotNull Location loc) {
        this(
            loc.getWorld() == null ? null : loc.getWorld().getUID(),
            loc.getWorld() == null ? "" : loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );
    }

    public FurnacePos(@NotNull Block block) {
        this(block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FurnacePos)) return false;
        FurnacePos that = (FurnacePos) o;
        if (this.x != that.x || this.y != that.y || this.z != that.z) return false;
        if (this.worldId != null && that.worldId != null) return this.worldId.equals(that.worldId);
        return Objects.equals(this.worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.worldId == null ? this.worldName : this.worldId, this.x, this.y, this.z);
    }

    @Override
    public String toString() {
        return this.worldName + " (" + this.x + ", " + this.y + ", " + this.z + ")";
    }
}
