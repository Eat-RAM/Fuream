package io.github.eat_ram.fuream.compat;

/** Version capabilities used by the Java 8 common core. */
public interface VersionAdapter {
    String id();

    boolean supportsSpecialFurnaces();

    boolean supportsRecipeOverrides();

    boolean usesDataComponents();
}
