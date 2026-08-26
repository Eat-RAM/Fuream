package io.github.eat_ram.fuream.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VersionAdaptersTest {
    @Test
    public void selectsAllFourFamiliesAtTheirBoundaries() {
        assertEquals("legacy-numeric", VersionAdapters.select(ServerVersion.parse("1.12.2")).id());
        assertEquals("flattening-obfuscated", VersionAdapters.select(ServerVersion.parse("1.13.2")).id());
        assertEquals("mojang-named", VersionAdapters.select(ServerVersion.parse("1.20.4")).id());
        assertEquals("components", VersionAdapters.select(ServerVersion.parse("1.20.5")).id());
    }

    @Test
    public void exposesEraCapabilities() {
        VersionAdapter legacy = VersionAdapters.select(ServerVersion.parse("1.7.10"));
        VersionAdapter components = VersionAdapters.select(ServerVersion.parse("26.2"));
        assertFalse(legacy.supportsSpecialFurnaces());
        assertFalse(legacy.supportsRecipeOverrides());
        assertTrue(components.supportsSpecialFurnaces());
        assertTrue(components.supportsRecipeOverrides());
        assertTrue(components.usesDataComponents());
    }
}
