package io.github.eat_ram.fuream.nbt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FurnaceRootNbtBridgeTest {
    @Test
    public void passiveProjectionKeepsEmptyFureamDataTag() {
        assertTrue(FurnaceRootNbtBridge.shouldKeepDataTag(true, true));
    }

    @Test
    public void populatedFureamDataTagIsAlwaysKept() {
        assertTrue(FurnaceRootNbtBridge.shouldKeepDataTag(false, false));
        assertTrue(FurnaceRootNbtBridge.shouldKeepDataTag(false, true));
    }

    @Test
    public void emptyActiveFureamDataTagMayBeRemoved() {
        assertFalse(FurnaceRootNbtBridge.shouldKeepDataTag(true, false));
    }
}
