package io.github.eat_ram.fuream.hook;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import io.github.eat_ram.fuream.FureamMain;
import io.github.eat_ram.fuream.api.FureamWorldConfig;
import io.github.eat_ram.fuream.api.FurnaceType;
import io.github.eat_ram.fuream.data.FureamData;
import io.github.eat_ram.fuream.data.FureamDataHolder;
import io.github.eat_ram.fuream.data.FureamFurnaceData;
import io.github.eat_ram.fuream.data.FurnaceFureamDataRegistry;
import io.github.eat_ram.fuream.logic.FureamFurnaceEngine;
import io.github.eat_ram.fuream.screen.FureamScreenHandler;
import io.github.eat_ram.fuream.screen.FureamScreenInventory;
import io.github.eat_ram.fuream.util.FurnacePos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FurnaceManager {
    public static final Map<FurnacePos, FurnaceContext> CONTEXTS = new ConcurrentHashMap<>();
    private static long tickCounter = 0;

    public static class FurnaceContext implements FureamDataHolder {
        public final @NotNull FurnacePos pos;
        public final @NotNull FurnaceType type;
        public final @NotNull FureamFurnaceData data;
        public final @NotNull Map<@NotNull String, @NotNull FureamData> extraData = new HashMap<>();

        public int burnTime;
        public int fuelTimeTotal;
        public int cookTime;
        public int cookTimeTotal;

        public @Nullable FureamScreenInventory activeGui;
        public @Nullable FureamScreenHandler handler;
        public boolean dirty;

        public FurnaceContext(@NotNull FurnacePos pos, @NotNull FurnaceType type) {
            this.pos = pos;
            this.type = type;
            this.data = new FureamFurnaceData();
            this.cookTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
            this.fuelTimeTotal = (type == FurnaceType.FURNACE ? 200 : 100);
        }

        public @Nullable World getWorld() {
            return Bukkit.getWorld(this.pos.worldName);
        }

        @Override
        public <T extends FureamData> @Nullable T getFureamData(String key, Class<T> clazz) {
            if ("fuream".equals(key)) {
                if (clazz.isInstance(this.data)) {
                    return clazz.cast(this.data);
                }
            }
            FureamData extra = this.extraData.get(key);
            if (clazz.isInstance(extra)) {
                return clazz.cast(extra);
            }
            return null;
        }
    }

    public static @Nullable FurnaceContext getOrCreateContext(@NotNull Block block) {
        FurnaceType type = FureamMain.getFurnaceType(block);
        if (type == null) return null;

        FurnacePos pos = new FurnacePos(block);
        FurnaceContext existing = CONTEXTS.get(pos);
        if (existing != null) {
            return existing;
        }

        FurnaceContext ctx = new FurnaceContext(pos, type);
        for (Map.Entry<String, java.util.function.Function<? super FureamDataHolder, ? extends FureamData>> entry :
             FurnaceFureamDataRegistry.REGISTRY.entrySet()) {
            if (!"fuream".equals(entry.getKey())) {
                ctx.extraData.put(entry.getKey(), entry.getValue().apply(ctx));
            }
        }

        BlockState state = block.getState();
        if (state instanceof Furnace) {
            try {
                NBT.getPersistentData(state, (ReadableNBT nbt) -> {
                    if (nbt != null && nbt.hasTag(FureamMain.DATA_KEY)) {
                        ReadableNBT dataCompound = nbt.getCompound(FureamMain.DATA_KEY);
                        ctx.data.readNbt(ctx, dataCompound);
                        for (Map.Entry<String, FureamData> entry : ctx.extraData.entrySet()) {
                            entry.getValue().readNbt(ctx, dataCompound);
                        }
                        if (nbt.hasTag("BurnTime")) {
                            ctx.burnTime = nbt.getInteger("BurnTime");
                        }
                        if (nbt.hasTag("CookTime")) {
                            ctx.cookTime = nbt.getInteger("CookTime");
                        }
                        if (nbt.hasTag("CookTimeTotal")) {
                            ctx.cookTimeTotal = nbt.getInteger("CookTimeTotal");
                        }
                    } else {
                        // Vanilla lane migration
                        Furnace furnaceState = (Furnace) state;
                        FurnaceInventory inv = furnaceState.getInventory();
                        ItemStack smelting = inv.getSmelting();
                        ItemStack fuel = inv.getFuel();
                        ItemStack result = inv.getResult();

                        if (smelting != null && !smelting.getType().isAir()) ctx.data.inputs.set(0, smelting.clone());
                        if (fuel != null && !fuel.getType().isAir()) ctx.data.fuels.set(0, fuel.clone());
                        if (result != null && !result.getType().isAir()) ctx.data.outputs.set(0, result.clone());

                        inv.clear();
                        ctx.burnTime = furnaceState.getBurnTime();
                        ctx.cookTime = furnaceState.getCookTime();
                        ctx.cookTimeTotal = furnaceState.getCookTimeTotal() > 0 ? furnaceState.getCookTimeTotal() : 200;
                        ctx.dirty = true;
                        saveToNbt(ctx);
                    }
                    return true;
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        CONTEXTS.put(pos, ctx);
        return ctx;
    }

    public static void saveToNbt(@NotNull FurnaceContext ctx) {
        World world = ctx.getWorld();
        if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) return;

        Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
        BlockState state = block.getState();
        if (state instanceof Furnace) {
            try {
                NBT.modifyPersistentData(state, (ReadWriteNBT nbt) -> {
                    ReadWriteNBT dataCompound = nbt.getOrCreateCompound(FureamMain.DATA_KEY);
                    ctx.data.writeNbt(ctx, dataCompound);
                    for (FureamData extra : ctx.extraData.values()) {
                        extra.writeNbt(ctx, dataCompound);
                    }
                    nbt.setInteger("BurnTime", ctx.burnTime);
                    nbt.setInteger("CookTime", ctx.cookTime);
                    nbt.setInteger("CookTimeTotal", ctx.cookTimeTotal);
                });
                ctx.dirty = false;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void tickAll() {
        Iterator<Map.Entry<FurnacePos, FurnaceContext>> it = CONTEXTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<FurnacePos, FurnaceContext> entry = it.next();
            FurnaceContext ctx = entry.getValue();
            World world = ctx.getWorld();

            if (world == null || !world.isChunkLoaded(ctx.pos.x >> 4, ctx.pos.z >> 4)) {
                if (ctx.dirty) {
                    saveToNbt(ctx);
                }
                it.remove();
                continue;
            }

            Block block = world.getBlockAt(ctx.pos.x, ctx.pos.y, ctx.pos.z);
            FurnaceType currentType = FureamMain.getFurnaceType(block);
            if (currentType == null) {
                // Block was removed or changed
                if (ctx.dirty) {
                    saveToNbt(ctx);
                }
                it.remove();
                continue;
            }

            FureamWorldConfig config = FureamMain.getWorldConfig(world);
            if (config != null && config.getEnabledFurnaceTypes().contains(ctx.type)) {
                FureamFurnaceEngine.tick(ctx, config);

                // Handle hopper below extraction (every 8 ticks, standard hopper speed)
                if (tickCounter % 8 == 0) {
                    processHopperPull(block, ctx);
                }
            }

            // Periodic save if dirty
            if (ctx.dirty) {
                if (tickCounter % 40 == 0) {
                    saveToNbt(ctx);
                }
            }
        }
        tickCounter++;
    }

    private static void processHopperPull(@NotNull Block furnaceBlock, @NotNull FurnaceContext ctx) {
        Block below = furnaceBlock.getRelative(BlockFace.DOWN);
        Inventory targetInv = null;

        if (below.getType() == Material.HOPPER) {
            if (below.getBlockData() instanceof org.bukkit.block.data.type.Hopper) {
                org.bukkit.block.data.type.Hopper hopperData = (org.bukkit.block.data.type.Hopper) below.getBlockData();
                if (hopperData.isEnabled()) {
                    BlockState belowState = below.getState();
                    if (belowState instanceof Hopper) {
                        targetInv = ((Hopper) belowState).getInventory();
                    }
                }
            }
        }

        if (targetInv == null) {
            Location centerBelow = furnaceBlock.getLocation().add(0.5, -0.5, 0.5);
            for (org.bukkit.entity.Entity e : furnaceBlock.getWorld().getNearbyEntities(centerBelow, 0.6, 0.6, 0.6)) {
                if (e instanceof HopperMinecart) {
                    targetInv = ((HopperMinecart) e).getInventory();
                    break;
                }
            }
        }

        if (targetInv == null) {
            return;
        }

        // 1. Try pulling from outputs first
        for (int i = 0; i < ctx.data.outputs.size(); i++) {
            ItemStack outStack = ctx.data.outputs.get(i);
            if (!outStack.getType().isAir() && outStack.getAmount() > 0) {
                ItemStack single = outStack.clone();
                single.setAmount(1);

                Map<Integer, ItemStack> leftover = targetInv.addItem(single);
                if (leftover.isEmpty()) {
                    outStack.setAmount(outStack.getAmount() - 1);
                    if (outStack.getAmount() <= 0) {
                        ctx.data.outputs.set(i, new ItemStack(Material.AIR));
                    }
                    if (ctx.activeGui != null) {
                        ctx.activeGui.refreshVisuals();
                    }
                    ctx.dirty = true;
                    return;
                }
            }
        }

        // 2. Try pulling empty buckets or water buckets from fuels
        for (int i = 0; i < ctx.data.fuels.size(); i++) {
            ItemStack fuelStack = ctx.data.fuels.get(i);
            if (fuelStack.getType() == Material.BUCKET || fuelStack.getType() == Material.WATER_BUCKET) {
                ItemStack single = fuelStack.clone();
                single.setAmount(1);

                Map<Integer, ItemStack> leftover = targetInv.addItem(single);
                if (leftover.isEmpty()) {
                    fuelStack.setAmount(fuelStack.getAmount() - 1);
                    if (fuelStack.getAmount() <= 0) {
                        ctx.data.fuels.set(i, new ItemStack(Material.AIR));
                    }
                    if (ctx.activeGui != null) {
                        ctx.activeGui.refreshVisuals();
                    }
                    ctx.dirty = true;
                    return;
                }
            }
        }
    }

    public static void flushAll() {
        for (FurnaceContext ctx : CONTEXTS.values()) {
            if (ctx.dirty || ctx.data.hasAny()) {
                saveToNbt(ctx);
            }
        }
    }
}
