package io.github.eat_ram.fuream;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.hook.BlockBreakListener;
import io.github.eat_ram.fuream.hook.ChunkListener;
import io.github.eat_ram.fuream.hook.FurnaceManager;
import io.github.eat_ram.fuream.hook.FurnaceOpenListener;
import io.github.eat_ram.fuream.hook.GuiListener;
import io.github.eat_ram.fuream.hook.HopperListener;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Smoker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FureamMain extends JavaPlugin implements Listener, CommandExecutor {
    public static final String DATA_KEY = "FureamData";
    public static final Map<@NotNull World, @NotNull FureamWorldConfig> WORLD_CONFIGS = new WeakHashMap<>();
    private static FureamMain INSTANCE;

    public static FureamMain getInstance() {
        return INSTANCE;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;

        if (!getServer().getPluginManager().isPluginEnabled("NBTAPI")) {
            getLogger().severe("Item-NBT-API is required for Fuream to operate! Please install NBT-API plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new FurnaceOpenListener(), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new HopperListener(), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(), this);

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
        if (getCommand("fuream") != null) {
            getCommand("fuream").setExecutor(this);
        }

        getLogger().info("Fuream (Spigot) enabled successfully!");
    }

    @Override
    public void onDisable() {
        FurnaceManager.flushAll();
        FurnaceManager.CONTEXTS.clear();
        getLogger().info("Fuream (Spigot) disabled successfully!");
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        loadWorldConfig(event.getWorld());
    }

    @EventHandler
    public void onWorldSave(WorldSaveEvent event) {
        FurnaceManager.flushAll();
    }

    public static void loadWorldConfig(@NotNull World world) {
        FureamWorldConfigImpl config = new FureamWorldConfigImpl();
        File configFile = new File(world.getWorldFolder(), "fuream.json");
        if (configFile.exists()) {
            try {
                FureamWorldConfigImpl.readWorldConfig(config, configFile);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to load fuream.json for world " + world.getName() + ": " + e.getMessage());
            }
        }
        WORLD_CONFIGS.put(world, config);
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
            if (mat == Material.FURNACE) return FurnaceType.FURNACE;
            if (mat == Material.SMOKER) return FurnaceType.SMOKER;
            if (mat == Material.BLAST_FURNACE) return FurnaceType.BLAST_FURNACE;
            return null;
        }
        if (obj instanceof BlockState) {
            if (obj instanceof BlastFurnace) return FurnaceType.BLAST_FURNACE;
            if (obj instanceof Smoker) return FurnaceType.SMOKER;
            if (obj instanceof Furnace) return FurnaceType.FURNACE;
            return null;
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
            sender.sendMessage("§a[Fuream] World configurations reloaded!");
            return true;
        }

        if (args.length > 0 && "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§e=== Fuream Status ===");
            for (World w : Bukkit.getWorlds()) {
                FureamWorldConfig cfg = getWorldConfig(w);
                if (cfg != null) {
                    sender.sendMessage("§6World: §f" + w.getName() + " §7(Enabled: " + cfg.getEnabledFurnaceTypes() + ")");
                }
            }
            return true;
        }

        sender.sendMessage("§6/fuream reload §7- Reload all world configs");
        sender.sendMessage("§6/fuream status §7- View status of all worlds");
        return true;
    }
}
