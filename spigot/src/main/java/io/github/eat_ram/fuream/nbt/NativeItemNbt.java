package io.github.eat_ram.fuream.nbt;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import io.github.eat_ram.fuream.compat.ItemCompat;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Native ItemStack codec, including registry-provider signatures used by 1.20.5+. */
public final class NativeItemNbt {
    private static Method asNmsCopy;
    private static Method asBukkitCopy;
    private static Object registryProvider;

    public static NativeNbtCompound write(ItemStack stack) {
        if (ItemCompat.isEmpty(stack)) return NativeNbtCompound.createForServer();
        try {
            ensureCraftMethods();
            Object nms = asNmsCopy.invoke(null, stack);
            NativeNbtCompound target = NativeNbtCompound.createForServer();
            for (Method method : nms.getClass().getMethods()) {
                if (Modifier.isStatic(method.getModifiers())) continue;
                Object[] args = serializationArguments(method.getParameterTypes(), target.raw());
                if (args == null) continue;
                Class<?> returnType = method.getReturnType();
                if (!returnType.isAssignableFrom(target.raw().getClass()) &&
                    !target.raw().getClass().isAssignableFrom(returnType)) continue;
                try {
                    Object result = method.invoke(nms, args);
                    if (result != null && target.raw().getClass().isInstance(result)) {
                        return new NativeNbtCompound(result);
                    }
                    return target;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
            throw new IllegalStateException("Unable to locate native ItemStack save method");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to serialize Bukkit ItemStack", e);
        }
    }

    public static ItemStack read(NativeNbtCompound compound) {
        if (compound == null || compound.keys().isEmpty()) return ItemCompat.empty();
        try {
            ensureCraftMethods();
            Class<?> nmsClass = asNmsCopy.getReturnType();
            for (Method method : nmsClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) continue;
                Object[] args = serializationArguments(method.getParameterTypes(), compound.raw());
                if (args == null) continue;
                if (method.getReturnType() != nmsClass && method.getReturnType() != Optional.class) continue;
                try {
                    Object result = method.invoke(null, args);
                    if (result instanceof Optional) result = ((Optional<?>) result).orElse(null);
                    if (result != null && nmsClass.isInstance(result)) {
                        ItemStack stack = (ItemStack) asBukkitCopy.invoke(null, result);
                        return stack == null ? ItemCompat.empty() : stack;
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return ItemCompat.empty();
    }

    private static Object[] serializationArguments(Class<?>[] parameterTypes, Object compound) {
        if (parameterTypes.length == 0 || parameterTypes.length > 2) return null;
        Object[] args = new Object[parameterTypes.length];
        boolean hasCompound = false;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> type = parameterTypes[i];
            if (type.isInstance(compound)) {
                args[i] = compound;
                hasCompound = true;
            } else if (type.getName().contains("HolderLookup") || type.getName().contains("RegistryAccess")) {
                Object provider = registryProvider(type);
                if (provider == null || !type.isInstance(provider)) return null;
                args[i] = provider;
            } else {
                return null;
            }
        }
        return hasCompound ? args : null;
    }

    private static void ensureCraftMethods() throws ReflectiveOperationException {
        if (asNmsCopy != null && asBukkitCopy != null) return;
        String craftRoot = Bukkit.getServer().getClass().getPackage().getName();
        Class<?> craftItemStack = Class.forName(craftRoot + ".inventory.CraftItemStack");
        asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
        Class<?> nmsClass = asNmsCopy.getReturnType();
        asBukkitCopy = craftItemStack.getMethod("asBukkitCopy", nmsClass);
    }

    private static Object registryProvider(Class<?> expectedType) {
        if (registryProvider != null && expectedType.isInstance(registryProvider)) return registryProvider;
        try {
            Object craftServer = Bukkit.getServer();
            Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            for (Method method : minecraftServer.getClass().getMethods()) {
                if (method.getParameterTypes().length == 0 && expectedType.isAssignableFrom(method.getReturnType())) {
                    Object result = method.invoke(minecraftServer);
                    if (result != null) {
                        registryProvider = result;
                        return result;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private NativeItemNbt() {
        throw new UnsupportedOperationException();
    }
}
