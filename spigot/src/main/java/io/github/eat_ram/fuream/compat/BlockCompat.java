package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;

import org.bukkit.Material;
import org.bukkit.block.Block;

public final class BlockCompat {
    public static void setLit(Block block, boolean lit) {
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            Method setLit = data.getClass().getMethod("setLit", boolean.class);
            setLit.invoke(data, lit);
            for (Method method : block.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("setBlockData".equals(method.getName()) && parameters.length == 2 &&
                    parameters[0].isInstance(data) && parameters[1] == boolean.class) {
                    method.invoke(block, data, false);
                    return;
                }
            }
            return;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        if (!"FURNACE".equals(block.getType().name()) && !"BURNING_FURNACE".equals(block.getType().name())) {
            return;
        }
        Material target = Material.matchMaterial(lit ? "BURNING_FURNACE" : "FURNACE");
        if (target == null || target == block.getType()) return;
        try {
            byte data = ((Number) block.getClass().getMethod("getData").invoke(block)).byteValue();
            Method getId = Material.class.getMethod("getId");
            int id = ((Number) getId.invoke(target)).intValue();
            block.getClass().getMethod("setTypeIdAndData", int.class, byte.class, boolean.class)
                .invoke(block, id, data, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private BlockCompat() {
        throw new UnsupportedOperationException();
    }
}
