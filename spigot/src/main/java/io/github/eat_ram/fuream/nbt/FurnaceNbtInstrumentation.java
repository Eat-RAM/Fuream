package io.github.eat_ram.fuream.nbt;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Adds the two persistence hooks that Fabric gets from its furnace mixin.
 * Spigot otherwise discards unknown block-entity root tags during load/save.
 */
public final class FurnaceNbtInstrumentation {
    private static final String INSTALLED_KEY = "io.github.eat_ram.fuream.root_nbt_instrumented";

    public static void install(JavaPlugin plugin) {
        FurnaceRootNbtBridge.installCallbacks();
        if (Boolean.TRUE.equals(System.getProperties().get(INSTALLED_KEY))) {
            return;
        }

        Class<?> furnaceClass = findFurnaceClass();
        Method loadMethod = findPersistenceMethod(furnaceClass, true);
        Method saveMethod = findPersistenceMethod(furnaceClass, false);
        Instrumentation instrumentation;
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (IllegalStateException defaultAttachmentFailure) {
            try {
                instrumentation = ByteBuddyAgent.install(
                    ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE
                );
                plugin.getLogger().info(
                    "Default JVM attachment unavailable; using bundled native attachment provider"
                );
            } catch (RuntimeException emulatedAttachmentFailure) {
                emulatedAttachmentFailure.addSuppressed(defaultAttachmentFailure);
                throw emulatedAttachmentFailure;
            }
        }

        if (!instrumentation.isModifiableClass(furnaceClass)) {
            throw new IllegalStateException("Furnace block entity class is not modifiable: " + furnaceClass.getName());
        }

        ElementMatcher.Junction<MethodDescription> loadMatcher = named(loadMethod.getName())
            .and(takesArguments(loadMethod.getParameterTypes()));
        ElementMatcher.Junction<MethodDescription> saveMatcher = named(saveMethod.getName())
            .and(takesArguments(saveMethod.getParameterTypes()));

        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .type(named(furnaceClass.getName()))
            .transform((builder, type, classLoader, module, protectionDomain) -> builder
                .visit(Advice.to(LoadAdvice.class).on(loadMatcher))
                .visit(Advice.to(SaveAdvice.class).on(saveMatcher)))
            .installOn(instrumentation);

        System.getProperties().put(INSTALLED_KEY, Boolean.TRUE);
        plugin.getLogger().info(
            "Installed Fabric-compatible furnace root-NBT hooks on " + furnaceClass.getName()
        );
    }

    private static Class<?> findFurnaceClass() {
        String[] candidates = {
            "net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity",
            "net.minecraft.world.level.block.entity.TileEntityFurnace"
        };
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate, false, Bukkit.getServer().getClass().getClassLoader());
            } catch (ClassNotFoundException ignored) {
            }
        }

        String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
        String version = craftPackage.substring(craftPackage.lastIndexOf('.') + 1);
        try {
            return Class.forName(
                "net.minecraft.server." + version + ".TileEntityFurnace",
                false,
                Bukkit.getServer().getClass().getClassLoader()
            );
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to locate the furnace block entity class", e);
        }
    }

    private static Method findPersistenceMethod(Class<?> furnaceClass, boolean load) {
        String[] preferredNames = load
            ? new String[] {"loadAdditional", "load", "loadData", "a"}
            : new String[] {"saveAdditional", "save", "saveData", "b"};
        List<Method> candidates = new ArrayList<>();
        for (Class<?> current = furnaceClass; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && hasCompoundArgument(method)) {
                    candidates.add(method);
                }
            }
        }

        for (String name : preferredNames) {
            for (Method method : candidates) {
                if (name.equals(method.getName()) && returnTypeMatches(method, load)) {
                    return method;
                }
            }
        }

        for (Method method : candidates) {
            int modifiers = method.getModifiers();
            boolean accessMatches = load
                ? Modifier.isPublic(modifiers)
                : Modifier.isProtected(modifiers) || !Modifier.isPublic(modifiers);
            if (accessMatches && returnTypeMatches(method, load)) return method;
        }

        throw new IllegalStateException(
            "Unable to locate furnace " + (load ? "load" : "save") + " method on " +
            furnaceClass.getName() + "; candidate methods=" + Arrays.toString(candidates.toArray())
        );
    }

    private static boolean returnTypeMatches(Method method, boolean load) {
        if (load) return method.getReturnType() == void.class;
        if (method.getReturnType() == void.class) return true;
        String name = method.getReturnType().getName();
        return name.endsWith(".CompoundTag") || name.endsWith(".NBTTagCompound");
    }

    private static boolean hasCompoundArgument(Method method) {
        for (Class<?> parameter : method.getParameterTypes()) {
            String name = parameter.getName();
            if (name.endsWith(".CompoundTag") || name.endsWith(".NBTTagCompound")) return true;
        }
        return false;
    }

    public static class LoadAdvice {
        @Advice.OnMethodExit
        @SuppressWarnings("unchecked")
        public static void exit(
            @Advice.This Object furnace,
            @Advice.AllArguments Object[] arguments
        ) {
            Object rootNbt = null;
            for (Object argument : arguments) {
                if (argument == null) continue;
                String name = argument.getClass().getName();
                if (name.endsWith(".CompoundTag") || name.endsWith(".NBTTagCompound")) {
                    rootNbt = argument;
                    break;
                }
            }
            if (rootNbt == null) return;
            Object callback = System.getProperties().get(
                "io.github.eat_ram.fuream.root_nbt_load_hook"
            );
            if (callback instanceof BiConsumer) {
                ((BiConsumer<Object, Object>) callback).accept(furnace, rootNbt);
            }
        }
    }

    public static class SaveAdvice {
        @Advice.OnMethodExit
        @SuppressWarnings("unchecked")
        public static void exit(
            @Advice.This Object furnace,
            @Advice.AllArguments Object[] arguments
        ) {
            Object rootNbt = null;
            for (Object argument : arguments) {
                if (argument == null) continue;
                String name = argument.getClass().getName();
                if (name.endsWith(".CompoundTag") || name.endsWith(".NBTTagCompound")) {
                    rootNbt = argument;
                    break;
                }
            }
            if (rootNbt == null) return;
            Object callback = System.getProperties().get(
                "io.github.eat_ram.fuream.root_nbt_save_hook"
            );
            if (callback instanceof BiConsumer) {
                ((BiConsumer<Object, Object>) callback).accept(furnace, rootNbt);
            }
        }
    }

    private FurnaceNbtInstrumentation() {
        throw new UnsupportedOperationException();
    }
}
