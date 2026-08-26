package io.github.eat_ram.fuream.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerVersionTest {
    @Test
    public void parsesClassicAndDateBasedVersions() {
        ServerVersion classic = ServerVersion.parse("1.20.6-R0.1-SNAPSHOT");
        assertEquals(1, classic.major);
        assertEquals(20, classic.minor);
        assertEquals(6, classic.patch);
        assertTrue(classic.atLeast(1, 20, 5));

        ServerVersion dateBased = ServerVersion.parse("26.2-R0.1-SNAPSHOT");
        assertEquals(26, dateBased.major);
        assertEquals(2, dateBased.minor);
        assertEquals("components", dateBased.adapterFamily());
        assertFalse(dateBased.atMost(1, 12));
    }
}
