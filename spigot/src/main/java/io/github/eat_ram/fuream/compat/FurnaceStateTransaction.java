package io.github.eat_ram.fuream.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.jetbrains.annotations.NotNull;

/**
 * Owns one furnace block-state snapshot from read through commit. Inventory and
 * timing fields must be changed through the same transaction.
 */
public final class FurnaceStateTransaction {
    private static final Map<Class<?>, Method> SNAPSHOT_INVENTORY_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> NO_SNAPSHOT_INVENTORY = new ConcurrentHashMap<>();

    private final Furnace state;
    private final FurnaceInventory inventory;
    private boolean changed;

    private FurnaceStateTransaction(@NotNull Furnace state) {
        this.state = state;
        FurnaceCompat.initialize(state);
        this.inventory = snapshotInventory(state);
    }

    public static FurnaceStateTransaction begin(@NotNull Block block) {
        BlockState state = block.getState();
        return state instanceof Furnace ? new FurnaceStateTransaction((Furnace) state) : null;
    }

    public @NotNull Furnace state() {
        return this.state;
    }

    public @NotNull FurnaceInventory inventory() {
        return this.inventory;
    }

    public void markChanged() {
        this.changed = true;
    }

    public void setTimes(int burnTime, int cookTime, int cookTimeTotal) {
        if (FurnaceCompat.getBurnTime(this.state) != burnTime) {
            FurnaceCompat.setBurnTime(this.state, burnTime);
            this.changed = true;
        }
        if (FurnaceCompat.getCookTime(this.state) != cookTime) {
            FurnaceCompat.setCookTime(this.state, cookTime);
            this.changed = true;
        }
        if (cookTimeTotal > 0 && FurnaceCompat.getCookTimeTotal(this.state, cookTimeTotal) != cookTimeTotal) {
            FurnaceCompat.setCookTimeTotal(this.state, cookTimeTotal);
            this.changed = true;
        }
    }

    public boolean commit() {
        return !this.changed || this.state.update(false, false);
    }

    private static FurnaceInventory snapshotInventory(Furnace furnace) {
        Class<?> type = furnace.getClass();
        Method method = SNAPSHOT_INVENTORY_METHODS.get(type);
        if (method == null && !NO_SNAPSHOT_INVENTORY.containsKey(type)) {
            try {
                method = type.getMethod("getSnapshotInventory");
                method.setAccessible(true);
                SNAPSHOT_INVENTORY_METHODS.put(type, method);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                NO_SNAPSHOT_INVENTORY.put(type, Boolean.TRUE);
            }
        }
        if (method != null) {
            try {
                Object result = method.invoke(furnace);
                if (result instanceof FurnaceInventory) {
                    return (FurnaceInventory) result;
                }
            } catch (ReflectiveOperationException ignored) {
                NO_SNAPSHOT_INVENTORY.put(type, Boolean.TRUE);
                SNAPSHOT_INVENTORY_METHODS.remove(type);
            }
        }
        return furnace.getInventory();
    }
}
