package io.github.eat_ram.fuream.compat;

import org.bukkit.block.Block;

/** Version capabilities used by the Java 8 common core. */
public interface VersionAdapter {
    String id();

    boolean supportsSpecialFurnaces();

    boolean supportsRecipeOverrides();

    boolean usesDataComponents();

    FurnaceStateTransaction beginFurnaceTransaction(Block block);

    void setFurnaceLit(Block block, boolean lit);

    void requestComparatorRecalculation(Block furnace);
}
