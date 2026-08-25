package io.github.eat_ram.fuream.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import io.github.eat_ram.fuream.logic.FureamFurnaceLogic;

/**
 * Spills the stored experience as orbs when a virtual furnace is broken.
 * The item contents are already spilled by the vanilla
 * {@code onStateReplaced} via {@code ItemScatterer}, which naturally sees
 * the redirected 27-slot virtual inventory.
 */
@Mixin(AbstractFurnaceBlock.class)
public abstract class AbstractFurnaceBlockMixin {
    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void dropExperienceOnBreak(
        BlockState state, World world
        , BlockPos pos, BlockState newState,
        boolean moved, CallbackInfo info
    ) {
        if (world.isClient || state.isOf(newState.getBlock())) {
            return;
        }
        BlockEntity be = world.getBlockEntity(pos);
        FurnaceType type = FureamMain.getFurnaceType(be);
        if (type != null) {
            MinecraftServer server = world.getServer();
            if (server != null) {
                FureamWorldConfig config =
                FureamMain.WORLD_CONFIGS.get(server);
                if (config != null &&
                    config.getEnabledFurnaceTypes().contains(type)) {
                    FureamFurnaceLogic.dropOnBreak(
                        (ServerWorld)world, pos, (AbstractFurnaceBlockEntity)be
                    );
                }
            }
        }
    }
}
