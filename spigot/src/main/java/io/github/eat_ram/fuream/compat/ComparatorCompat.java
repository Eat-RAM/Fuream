package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.logic.FuelTable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;

/** Maintains the comparator view exposed by Fabric's three-slot masked inventory. */
public final class ComparatorCompat {
    private static final BlockFace[] HORIZONTAL = {
        BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    public static int calculate(FureamFurnaceData data) {
        ItemStack input = first(data.inputs, false);
        ItemStack fuel = first(data.fuels, true);
        ItemStack output = first(data.outputs, false);
        ItemStack[] masked = {input, fuel, output};
        float fullness = 0f;
        int occupied = 0;
        for (ItemStack stack : masked) {
            if (ItemCompat.isEmpty(stack)) continue;
            fullness += Math.min(1f, (float) stack.getAmount() / Math.max(1, stack.getMaxStackSize()));
            occupied++;
        }
        return occupied == 0 ? 0 : (int) Math.floor(fullness / 3f * 14f) + 1;
    }

    public static void updateAround(Block furnace, int signal) {
        for (BlockFace face : HORIZONTAL) {
            Block comparator = furnace.getRelative(face);
            if (!isComparator(comparator.getType()) ||
                !face.getOppositeFace().name().equals(facingName(comparator))) continue;
            setComparatorOutput(comparator, signal);
        }
    }

    public static Block sourceBlock(Block comparator) {
        try {
            BlockFace facing = BlockFace.valueOf(facingName(comparator));
            return comparator.getRelative(facing);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ItemStack first(List<ItemStack> stacks, boolean fuelOnly) {
        for (ItemStack stack : stacks) {
            if (!ItemCompat.isEmpty(stack) && (!fuelOnly || FuelTable.isFuel(stack))) return stack;
        }
        return ItemCompat.empty();
    }

    private static boolean isComparator(Material material) {
        String name = material.name();
        return name.contains("COMPARATOR");
    }

    private static String facingName(Block comparator) {
        try {
            Object data = comparator.getClass().getMethod("getBlockData").invoke(comparator);
            Method method = data.getClass().getMethod("getFacing");
            method.setAccessible(true);
            Object facing = method.invoke(data);
            return facing instanceof Enum ? ((Enum<?>) facing).name() : String.valueOf(facing);
        } catch (ReflectiveOperationException ignored) {
            try {
                int data = ((Number) comparator.getClass().getMethod("getData").invoke(comparator)).intValue() & 3;
                return new String[] {"NORTH", "EAST", "SOUTH", "WEST"}[data];
            } catch (ReflectiveOperationException e) {
                return "";
            }
        }
    }

    private static void setComparatorOutput(Block comparator, int signal) {
        setPowerState(comparator, signal > 0, false);
        try {
            BlockState state = comparator.getState();
            Method tileMethod = findTileEntityMethod(state.getClass());
            Object tile = tileMethod.invoke(state);
            Field output = findOutputField(tile.getClass());
            output.setInt(tile, Math.max(0, Math.min(15, signal)));
            markChanged(tile);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        notifyNeighbors(comparator);
    }

    private static Method findTileEntityMethod(Class<?> type) throws NoSuchMethodException {
        for (String name : new String[] {"getTileEntityFromWorld", "getTileEntity"}) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                        method.setAccessible(true);
                        return method;
                    }
                }
            }
        }
        throw new NoSuchMethodException("No comparator tile entity accessor on " + type.getName());
    }

    private static Field findOutputField(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class) {
                    field.setAccessible(true);
                    return field;
                }
            }
        }
        throw new IllegalStateException("Comparator output field is unavailable on " + type.getName());
    }

    private static void markChanged(Object tile) {
        for (String name : new String[] {"setChanged", "markDirty"}) {
            try {
                Method method = tile.getClass().getMethod(name);
                method.invoke(tile);
                return;
            } catch (ReflectiveOperationException ignored) {
            }
        }
    }

    private static void setPowerState(Block block, boolean powered, boolean physics) {
        try {
            Object data = block.getClass().getMethod("getBlockData").invoke(block);
            Method poweredSetter = data.getClass().getMethod("setPowered", boolean.class);
            poweredSetter.setAccessible(true);
            poweredSetter.invoke(data, powered);
            for (Method method : block.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("setBlockData".equals(method.getName()) && parameters.length == 2 &&
                    parameters[0].isInstance(data) && parameters[1] == boolean.class) {
                    method.invoke(block, data, physics);
                    return;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            byte data = ((Number) block.getClass().getMethod("getData").invoke(block)).byteValue();
            Material target = Material.matchMaterial(powered
                ? "REDSTONE_COMPARATOR_ON" : "REDSTONE_COMPARATOR_OFF");
            if (target == null) return;
            int id = ((Number) Material.class.getMethod("getId").invoke(target)).intValue();
            block.getClass().getMethod("setTypeIdAndData", int.class, byte.class, boolean.class)
                .invoke(block, id, data, physics);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void notifyNeighbors(Block block) {
        try {
            Object craftWorld = block.getWorld();
            Object handle = craftWorld.getClass().getMethod("getHandle").invoke(craftWorld);
            ClassLoader loader = handle.getClass().getClassLoader();
            String craftPackage = craftWorld.getClass().getPackage().getName();
            String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
            Class<?> positionClass = null;
            for (String candidate : new String[] {
                "net.minecraft.core.BlockPos", "net.minecraft.core.BlockPosition",
                "net.minecraft.server." + version + ".BlockPosition"
            }) {
                try {
                    positionClass = Class.forName(candidate, false, loader);
                    break;
                } catch (ClassNotFoundException ignored) {
                }
            }
            if (positionClass == null) return;
            Object position = positionClass.getConstructor(int.class, int.class, int.class)
                .newInstance(block.getX(), block.getY(), block.getZ());

            String craftRoot = org.bukkit.Bukkit.getServer().getClass().getPackage().getName();
            Class<?> magicNumbers = Class.forName(craftRoot + ".util.CraftMagicNumbers");
            Object nmsBlock = magicNumbers.getMethod("getBlock", Material.class)
                .invoke(null, block.getType());
            if (nmsBlock == null) return;

            Object blockState = null;
            for (Method method : handle.getClass().getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && parameters[0].isInstance(position) &&
                    (method.getReturnType().getSimpleName().equals("IBlockData") ||
                     method.getReturnType().getSimpleName().equals("BlockState"))) {
                    blockState = method.invoke(handle, position);
                    if (blockState != null) break;
                }
            }
            if (blockState != null) {
                for (Class<?> current = nmsBlock.getClass(); current != null; current = current.getSuperclass()) {
                    for (Method method : current.getDeclaredMethods()) {
                        Class<?>[] parameters = method.getParameterTypes();
                        if (Modifier.isStatic(method.getModifiers()) || parameters.length != 3 ||
                            !parameters[0].isInstance(handle) || !parameters[1].isInstance(position) ||
                            !parameters[2].isInstance(blockState) || method.getReturnType() != void.class) continue;
                        String name = method.getName();
                        if (!"updateNeighborsInFront".equals(name) && !"d".equals(name)) continue;
                        method.setAccessible(true);
                        method.invoke(nmsBlock, handle, position, blockState);
                        return;
                    }
                }
            }

            for (Class<?> current = handle.getClass(); current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (Modifier.isStatic(method.getModifiers()) || parameters.length != 2 ||
                        !parameters[0].isInstance(position) || !parameters[1].isInstance(nmsBlock) ||
                        method.getReturnType() != void.class) continue;
                    String name = method.getName();
                    if (!"updateNeighborsAt".equals(name) && !"applyPhysics".equals(name) &&
                        !"notifyNeighborsOfStateChange".equals(name) && !"b".equals(name)) continue;
                    method.setAccessible(true);
                    method.invoke(handle, position, nmsBlock);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private ComparatorCompat() {
        throw new UnsupportedOperationException();
    }
}
