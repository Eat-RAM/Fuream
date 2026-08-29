package io.github.eat_ram.fuream;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import org.jetbrains.annotations.NotNull;

/** Validated, immutable values consumed by furnace and inventory hot paths. */
public final class ResolvedFurnaceConfig {
    public static final int MAX_LANE_SLOTS = 576;

    public final int inputSlots;
    public final int fuelSlots;
    public final int outputSlots;
    public final boolean preventNonSmeltableHopperInput;

    private ResolvedFurnaceConfig(
        int inputSlots, int fuelSlots, int outputSlots,
        boolean preventNonSmeltableHopperInput
    ) {
        this.inputSlots = inputSlots;
        this.fuelSlots = fuelSlots;
        this.outputSlots = outputSlots;
        this.preventNonSmeltableHopperInput = preventNonSmeltableHopperInput;
    }

    public static @NotNull ResolvedFurnaceConfig resolve(
        @NotNull FureamWorldConfig config, @NotNull FurnaceType type
    ) {
        return new ResolvedFurnaceConfig(
            clamp(config.getInputSlotCount().get(type)),
            clamp(config.getFuelSlotCount().get(type)),
            clamp(config.getOutputSlotCount().get(type)),
            config.getPreventsHopperInsertNonSmeltable().contains(type)
        );
    }

    private static int clamp(Integer value) {
        return Math.max(1, Math.min(MAX_LANE_SLOTS, value == null ? 9 : value));
    }
}
