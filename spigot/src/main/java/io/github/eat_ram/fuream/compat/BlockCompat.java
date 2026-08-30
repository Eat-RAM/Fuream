package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.Block;

public final class BlockCompat {
    private static final Map<Class<?>, ModernAccessors> MODERN = new ConcurrentHashMap<>();
    private static final Set<Class<?>> NO_MODERN = Collections.newSetFromMap(
        new ConcurrentHashMap<Class<?>, Boolean>()
    );
    private static final Map<Class<?>, LegacyAccessors> LEGACY = new ConcurrentHashMap<>();

    public static void setLit(Block block, boolean lit) {
        try {
            ModernAccessors accessors = modern(block);
            if (accessors != null) {
                Object data = accessors.getBlockData.invoke(block);
                Method setLit = accessors.setLit(data.getClass());
                setLit.invoke(data, lit);
                accessors.setBlockData.invoke(block, data, false);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        if (!"FURNACE".equals(block.getType().name()) && !"BURNING_FURNACE".equals(block.getType().name())) {
            return;
        }
        Material target = Material.matchMaterial(lit ? "BURNING_FURNACE" : "FURNACE");
        if (target == null || target == block.getType()) return;
        try {
            LegacyAccessors accessors = legacy(block.getClass());
            byte data = ((Number) accessors.getData.invoke(block)).byteValue();
            int id = ((Number) accessors.getMaterialId.invoke(target)).intValue();
            accessors.setTypeAndData.invoke(block, id, data, false);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static ModernAccessors modern(Block block) {
        Class<?> type = block.getClass();
        ModernAccessors cached = MODERN.get(type);
        if (cached != null) return cached;
        if (NO_MODERN.contains(type)) return null;
        try {
            ModernAccessors created = new ModernAccessors(type);
            ModernAccessors existing = MODERN.putIfAbsent(type, created);
            return existing == null ? created : existing;
        } catch (ReflectiveOperationException failure) {
            NO_MODERN.add(type);
            return null;
        }
    }

    private static LegacyAccessors legacy(Class<?> type) throws ReflectiveOperationException {
        LegacyAccessors cached = LEGACY.get(type);
        if (cached != null) return cached;
        LegacyAccessors created = new LegacyAccessors(type);
        LegacyAccessors existing = LEGACY.putIfAbsent(type, created);
        return existing == null ? created : existing;
    }

    private static final class ModernAccessors {
        private final Method getBlockData;
        private final Method setBlockData;
        private final Map<Class<?>, Method> setLit = new ConcurrentHashMap<>();

        private ModernAccessors(Class<?> blockType) throws ReflectiveOperationException {
            this.getBlockData = blockType.getMethod("getBlockData");
            Class<?> blockDataType = this.getBlockData.getReturnType();
            this.setBlockData = blockType.getMethod("setBlockData", blockDataType, boolean.class);
        }

        private Method setLit(Class<?> dataType) throws ReflectiveOperationException {
            Method cached = this.setLit.get(dataType);
            if (cached != null) return cached;
            Method created = dataType.getMethod("setLit", boolean.class);
            Method existing = this.setLit.putIfAbsent(dataType, created);
            return existing == null ? created : existing;
        }
    }

    private static final class LegacyAccessors {
        private final Method getData;
        private final Method getMaterialId;
        private final Method setTypeAndData;

        private LegacyAccessors(Class<?> blockType) throws ReflectiveOperationException {
            this.getData = blockType.getMethod("getData");
            this.getMaterialId = Material.class.getMethod("getId");
            this.setTypeAndData = blockType.getMethod(
                "setTypeIdAndData", int.class, byte.class, boolean.class
            );
        }
    }

    private BlockCompat() {
    }
}
