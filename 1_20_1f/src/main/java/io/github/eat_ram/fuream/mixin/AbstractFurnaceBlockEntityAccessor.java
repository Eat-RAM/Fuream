package io.github.eat_ram.fuream.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.World;

@Mixin(net.minecraft.block.entity.AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
    @Accessor("burnTime")
    int getBurnTime();

    @Accessor("fuelTime")
    int getFuelTime();

    @Accessor("cookTime")
    int getCookTime();

    @Accessor("cookTime")
    void setCookTime(int cookTime);

    @Accessor("cookTimeTotal")
    int getCookTimeTotal();

    @Accessor("cookTimeTotal")
    void setCookTimeTotal(int cookTimeTotal);

    @Invoker("getCookTime")
    public static int
    invokeGetCookTime(World world, AbstractFurnaceBlockEntity furnace) {
        throw new AssertionError();
    }
}
