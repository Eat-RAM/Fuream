package io.github.eat_ram.fuream;

import java.util.List;

import io.github.eat_ram.fuream.data.FureamFurnaceData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HopperAndDataTest {

    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType().isAir() || b.getType().isAir()) return false;
        return a.getType() == b.getType();
    }

    private boolean insertIntoList(List<ItemStack> list, ItemStack item, int maxSlots) {
        while (list.size() < maxSlots) {
            list.add(new ItemStack(Material.AIR));
        }

        // 1. Try stacking with existing matching items
        for (int i = 0; i < maxSlots && i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s != null && isSameItem(s, item) && s.getAmount() < s.getMaxStackSize()) {
                s.setAmount(s.getAmount() + 1);
                return true;
            }
        }

        // 2. Try empty slot
        for (int i = 0; i < maxSlots && i < list.size(); i++) {
            ItemStack s = list.get(i);
            if (s == null || s.getType().isAir()) {
                ItemStack single = item.clone();
                single.setAmount(1);
                list.set(i, single);
                return true;
            }
        }

        return false;
    }

    @Test
    public void test64ItemsInsertion() {
        FureamFurnaceData data = new FureamFurnaceData();
        int maxSlots = 18;

        ItemStack rawIron = new ItemStack(Material.RAW_IRON, 1);
        for (int i = 0; i < 64; i++) {
            boolean inserted = insertIntoList(data.inputs, rawIron, maxSlots);
            assertTrue(inserted, "Should insert item " + (i + 1));
        }

        assertEquals(Material.RAW_IRON, data.inputs.get(0).getType());
        assertEquals(64, data.inputs.get(0).getAmount());
    }

    @Test
    public void testOver64ItemsInsertion() {
        FureamFurnaceData data = new FureamFurnaceData();
        int maxSlots = 18;

        ItemStack rawIron = new ItemStack(Material.RAW_IRON, 1);
        for (int i = 0; i < 100; i++) {
            boolean inserted = insertIntoList(data.inputs, rawIron, maxSlots);
            assertTrue(inserted, "Should insert item " + (i + 1));
        }

        assertEquals(Material.RAW_IRON, data.inputs.get(0).getType());
        assertEquals(64, data.inputs.get(0).getAmount());
        assertEquals(Material.RAW_IRON, data.inputs.get(1).getType());
        assertEquals(36, data.inputs.get(1).getAmount());
    }
}
