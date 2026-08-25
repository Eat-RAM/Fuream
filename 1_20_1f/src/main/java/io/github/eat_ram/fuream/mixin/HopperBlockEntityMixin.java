package io.github.eat_ram.fuream.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.util.FurnaceForHopperInventory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.Hopper;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(value = net.minecraft.block.entity.HopperBlockEntity.class,
       priority = 5000)
public abstract class HopperBlockEntityMixin {
    @ModifyVariable(method = "insert", at = @At(
        value = "INVOKE_ASSIGN",
        target =
        "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/inventory/Inventory;"
    ), ordinal = 1)
    private static Inventory wrap(
        Inventory inventory2, World world, BlockPos pos, BlockState state,
        Inventory /* hopper */inventory
    ) {
        if (inventory2 instanceof AbstractFurnaceBlockEntity) {
            FurnaceType type = FureamMain.getFurnaceType(inventory2);
            if (type != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null &&
                        config.getEnabledFurnaceTypes().contains(type)) {
                        return new FurnaceForHopperInventory(
                            (AbstractFurnaceBlockEntity)inventory2
                        );
                    }
                }
            }
        }
        return inventory2;
    }

    @ModifyReturnValue(method = "getInputInventory", at = @At("RETURN"))
    private static Inventory
    wrap(Inventory original, World world, Hopper hopper) {
        if (original instanceof AbstractFurnaceBlockEntity) {
            FurnaceType type = FureamMain.getFurnaceType(original);
            if (type != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null &&
                        config.getEnabledFurnaceTypes().contains(type)) {
                        return new FurnaceForHopperInventory(
                            (AbstractFurnaceBlockEntity)original
                        );
                    }
                }
            }
        }
        return original;
    }

    // For Lithium compatibility; however no conditional-mixin needed.
    @ModifyVariable(
        method =
        "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z",
        at = @At(
            value = "INVOKE_ASSIGN",
            target =
            "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"
        ), ordinal = 0
    )
    private static Inventory
    wrapJellysquid(Inventory inventory, World world, Hopper hopper) {
        if (inventory instanceof AbstractFurnaceBlockEntity) {
            FurnaceType type = FureamMain.getFurnaceType(inventory);
            if (type != null) {
                MinecraftServer server = world.getServer();
                if (server != null) {
                    FureamWorldConfig config =
                    FureamMain.WORLD_CONFIGS.get(server);
                    if (config != null &&
                        config.getEnabledFurnaceTypes().contains(type)) {
                        return new FurnaceForHopperInventory(
                            (AbstractFurnaceBlockEntity)inventory
                        );
                    }
                }
            }
        }
        return inventory;
    }
}
