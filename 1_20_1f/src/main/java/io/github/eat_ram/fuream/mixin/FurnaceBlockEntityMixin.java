package io.github.eat_ram.fuream.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import io.github.eat_ram.fuream.screen.FureamScreenHandler;
import net.minecraft.server.MinecraftServer;

/**
 * Replaces the vanilla furnace screen handler with the virtual 9x6 one.
 * The client only receives an {@code OpenScreenS2CPacket} of type
 * {@link net.minecraft.screen.ScreenHandlerType#GENERIC_9X6} and renders the
 * vanilla generic 9x6 screen; no client mod is involved.
 */
@Mixin(net.minecraft.block.entity.FurnaceBlockEntity.class)
public abstract class FurnaceBlockEntityMixin {
    @Inject(method = "createScreenHandler", at = @At("HEAD"),
            cancellable = true)
    private void createFureamScreenHandler(
        int syncId, PlayerInventory playerInventory,
        CallbackInfoReturnable<ScreenHandler> info
    ) {
        MinecraftServer server = playerInventory.player.getServer();
        if (server != null) {
            FureamWorldConfig config = FureamMain.WORLD_CONFIGS.get(server);
            if (config != null &&
                config.getEnabledFurnaceTypes()
                .contains(FurnaceType.FURNACE)) {
                info.setReturnValue(new FureamScreenHandler(
                    syncId, playerInventory,
                    (AbstractFurnaceBlockEntity)(Object)this
                ));
            }
        }
    }
}
