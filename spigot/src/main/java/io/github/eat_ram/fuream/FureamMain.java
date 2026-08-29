package io.github.eat_ram.fuream;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.logging.Level;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.BlockBreakListener;
import io.github.eat_ram.fuream.hook.BlockExplodeListener;
import io.github.eat_ram.fuream.hook.ChunkListener;
import io.github.eat_ram.fuream.hook.ComparatorListener;
import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceOpenListener;
import io.github.eat_ram.fuream.hook.GuiListener;
import io.github.eat_ram.fuream.hook.HopperListener;
import io.github.eat_ram.fuream.hook.VanillaFurnaceGuardListener;
import io.github.eat_ram.fuream.hook.RecipeIndexListener;
import io.github.eat_ram.fuream.compat.RecipeCompat;
import io.github.eat_ram.fuream.compat.FurnaceEventCompat;
import io.github.eat_ram.fuream.compat.ServerVersion;
import io.github.eat_ram.fuream.compat.VersionAdapters;
import io.github.eat_ram.fuream.nbt.FurnaceNbtInstrumentation;
import io.github.eat_ram.fuream.nbt.FurnaceRootNbtBridge;
import io.github.eat_ram.fuream.nbt.NbtSelfTest;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FureamMain extends JavaPlugin implements Listener, TabExecutor {
    public static final String DATA_KEY = "FureamData";
    private static final Map<@NotNull World, @NotNull FureamWorldConfig> WORLD_CONFIGS = new WeakHashMap<>();
    private static final Map<@NotNull World, @NotNull File> WORLD_CONFIG_SOURCES = new WeakHashMap<>();
    private static final Map<java.util.UUID, String> WORLD_CONFIG_ERRORS = new HashMap<>();
    private static FureamMain INSTANCE;
    private boolean rootNbtReady;

    public static FureamMain getInstance() {
        return INSTANCE;
    }

    @Override
    public void onLoad() {
        try {
            FurnaceNbtInstrumentation.install(this);
            this.rootNbtReady = true;
        } catch (Throwable e) {
            this.getLogger().severe(
                "Unable to install furnace root-NBT hooks; refusing to fall back to PDC: " + e
            );
            this.getLogger().log(Level.SEVERE, "Root-NBT hook installation failed", e);
        }
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        if (!this.rootNbtReady) {
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            NbtSelfTest.run();
        } catch (Throwable failure) {
            this.rootNbtReady = false;
            this.getLogger().log(Level.SEVERE, "Native NBT self-test failed; no furnaces were adopted", failure);
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register listeners
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new FurnaceOpenListener(), this);
        this.getServer().getPluginManager().registerEvents(new GuiListener(), this);
        this.getServer().getPluginManager().registerEvents(new HopperListener(), this);
        this.getServer().getPluginManager().registerEvents(new VanillaFurnaceGuardListener(), this);
        this.getServer().getPluginManager().registerEvents(new RecipeIndexListener(), this);
        this.getServer().getPluginManager().registerEvents(new ComparatorListener(), this);
        this.getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        try {
            Class.forName("org.bukkit.event.block.BlockExplodeEvent", false, this.getClassLoader());
            this.getServer().getPluginManager().registerEvents(new BlockExplodeListener(), this);
        } catch (ClassNotFoundException ignored) {
            // BlockExplodeEvent was added after the 1.7 API.
        }
        this.getServer().getPluginManager().registerEvents(new ChunkListener(), this);

        FurnaceEventCompat.initialize();
        RecipeCompat.rebuild();

        // Load configs and scan loaded chunks for existing furnaces
        for (World world : Bukkit.getWorlds()) {
            loadWorldConfig(world);
            for (Chunk chunk : world.getLoadedChunks()) {
                for (BlockState tile : chunk.getTileEntities()) {
                    if (tile instanceof Furnace) {
                        FurnaceManager.getOrCreateContext(tile.getBlock());
                    }
                }
            }
        }

        // Schedule tick loop
        Bukkit.getScheduler().runTaskTimer(this, FurnaceManager::tickAll, 1L, 1L);

        // Register command
        if (this.getCommand("fuream") != null) {
            this.getCommand("fuream").setExecutor(this);
            this.getCommand("fuream").setTabCompleter(this);
        }

        this.getLogger().info("Fuream (Spigot) enabled successfully!");
    }

    @Override
    public void onDisable() {
        FurnaceManager.shutdown();
        this.getLogger().info("Fuream (Spigot) disabled successfully!");
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        loadWorldConfig(event.getWorld());
    }

    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        FurnaceManager.flushWorld(event.getWorld());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        FurnaceManager.unloadWorld(event.getWorld());
        WORLD_CONFIGS.remove(event.getWorld());
        WORLD_CONFIG_SOURCES.remove(event.getWorld());
        WORLD_CONFIG_ERRORS.remove(event.getWorld().getUID());
    }

    public static void loadWorldConfig(@NotNull World world) {
        FureamWorldConfigImpl config = new FureamWorldConfigImpl();
        File targetFile = findExistingConfig(world.getWorldFolder());
        boolean dimension = world.getEnvironment() == World.Environment.NETHER ||
            world.getEnvironment() == World.Environment.THE_END;
        File mainWorldFolder = getMainWorldFolder();
        if (targetFile == null && dimension) {
            targetFile = findExistingConfig(mainWorldFolder);
        }
        if (targetFile == null && !dimension) {
            targetFile = new File(world.getWorldFolder(), "fuream.json");
            try {
                FureamWorldConfigImpl.writeWorldConfig(config, targetFile);
            } catch (Exception e) {
                recordConfigFailure(world, targetFile, e);
                return;
            }
        }

        if (targetFile != null && targetFile.exists()) {
            try {
                FureamWorldConfigImpl.readWorldConfig(config, targetFile);
            } catch (Exception e) {
                recordConfigFailure(world, targetFile, e);
                return;
            }
        }
        WORLD_CONFIGS.put(world, config);
        WORLD_CONFIG_ERRORS.remove(world.getUID());
        if (targetFile == null) {
            WORLD_CONFIG_SOURCES.remove(world);
        } else {
            WORLD_CONFIG_SOURCES.put(world, targetFile.getAbsoluteFile());
        }
    }

    private static void recordConfigFailure(World world, File source, Exception failure) {
        String message = "Failed to load " + source + " for world " + world.getName() + ": " + failure.getMessage();
        WORLD_CONFIG_ERRORS.put(world.getUID(), message);
        Bukkit.getLogger().warning(message + "; retaining the last-known-good configuration");
    }

    private static @Nullable File findExistingConfig(@NotNull File worldFolder) {
        File direct = new File(worldFolder, "fuream.json");
        if (direct.isFile()) return direct;
        File serverConfig = new File(new File(worldFolder, "serverconfig"), "fuream.json");
        if (serverConfig.isFile()) return serverConfig;
        File json5 = new File(new File(worldFolder, "serverconfig"), "fuream.json5");
        return json5.isFile() ? json5 : null;
    }

    private static @NotNull File getMainWorldFolder() {
        File container = Bukkit.getWorldContainer();
        Properties properties = new Properties();
        File propertiesFile = new File("server.properties");
        try (FileInputStream input = new FileInputStream(propertiesFile)) {
            properties.load(input);
        } catch (Exception ignored) {
        }
        String levelName = properties.getProperty("level-name", "world").trim();
        if (levelName.isEmpty()) levelName = "world";
        return new File(container, levelName);
    }

    public static @Nullable FureamWorldConfig getWorldConfig(@Nullable World world) {
        if (world == null) return null;
        FureamWorldConfig config = WORLD_CONFIGS.get(world);
        if (config == null) {
            loadWorldConfig(world);
            config = WORLD_CONFIGS.get(world);
        }
        return config;
    }

    @Contract(value = "null -> null", pure = true)
    public static @Nullable FurnaceType getFurnaceType(@Nullable Object obj) {
        if (obj instanceof FurnaceType) {
            return (FurnaceType) obj;
        }
        if (obj instanceof String) {
            try {
                return FurnaceType.valueOf(((String) obj).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
            return null;
        }
        if (obj instanceof Block) {
            Material mat = ((Block) obj).getType();
            String materialName = mat.name();
            if ("FURNACE".equals(materialName) || "BURNING_FURNACE".equals(materialName)) {
                return FurnaceType.FURNACE;
            }
            if ("SMOKER".equals(materialName)) return FurnaceType.SMOKER;
            if ("BLAST_FURNACE".equals(materialName)) return FurnaceType.BLAST_FURNACE;
            return null;
        }
        if (obj instanceof BlockState) {
            return getFurnaceType(((BlockState) obj).getBlock());
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("fuream.reload")) {
                sender.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }
            for (World w : Bukkit.getWorlds()) {
                loadWorldConfig(w);
            }
            RecipeCompat.rebuild();
            FurnaceManager.reconcileConfiguration();
            boolean failed = false;
            for (World world : Bukkit.getWorlds()) {
                String error = WORLD_CONFIG_ERRORS.get(world.getUID());
                if (error != null) {
                    failed = true;
                    sender.sendMessage("§c[Fuream] " + error);
                }
            }
            sender.sendMessage(failed
                ? "§e[Fuream] Reload completed with errors; previous configs remain active."
                : "§a[Fuream] World configurations reloaded!");
            return true;
        }

        if (args.length > 0 && "status".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("fuream.status")) {
                sender.sendMessage("§cYou do not have permission to use this command.");
                return true;
            }
            sender.sendMessage("§e=== Fuream Status ===");
            sender.sendMessage("§6Version adapter: §f" + VersionAdapters.current().id() +
                " §7(" + ServerVersion.CURRENT + ")");
            sender.sendMessage("§6Root NBT hook: §f" + (this.rootNbtReady ? "ready" : "failed"));
            long nbtFailures = FurnaceRootNbtBridge.failureCount();
            sender.sendMessage("§6Root NBT runtime failures: §f" + nbtFailures);
            if (nbtFailures > 0) sender.sendMessage("§6Last NBT failure: §f" + FurnaceRootNbtBridge.lastFailure());
            sender.sendMessage("§6Indexed cooking recipes: §f" + RecipeCompat.indexedRecipeCount());
            for (World w : Bukkit.getWorlds()) {
                FureamWorldConfig cfg = getWorldConfig(w);
                if (cfg != null) {
                    File source = WORLD_CONFIG_SOURCES.get(w);
                    sender.sendMessage("§6World: §f" + w.getName() + " §7(Enabled: " +
                        cfg.getEnabledFurnaceTypes() + ", Source: " +
                        (source == null ? "in-memory defaults" : source.getPath()) + ")");
                }
                String configError = WORLD_CONFIG_ERRORS.get(w.getUID());
                if (configError != null) sender.sendMessage("§cConfig error: §f" + configError);
            }
            return true;
        }

        sender.sendMessage("§6/fuream reload §7- Reload all world configs");
        sender.sendMessage("§6/fuream status §7- View status of all worlds");
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender, @NotNull Command command,
        @NotNull String alias, @NotNull String[] args
    ) {
        if (args.length != 1) return java.util.Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (sender.hasPermission("fuream.reload") && "reload".startsWith(prefix)) result.add("reload");
        if (sender.hasPermission("fuream.status") && "status".startsWith(prefix)) result.add("status");
        return result;
    }
}
