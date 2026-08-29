package io.github.eat_ram.fuream.nbt;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Startup round trips for the NBT primitives required before any furnace is adopted. */
public final class NbtSelfTest {
    public static void run() {
        NativeItemNbt.initialize();
        NativeNbtCompound compound = NativeNbtCompound.createForServer();
        compound.setInt("Int", 37);
        compound.setString("String", "fuream");
        compound.setFloat("Float", 1.25f);
        NativeNbtList list = compound.resetList("List");
        NativeNbtCompound child = NativeNbtCompound.createForServer();
        child.setInt("Value", 9);
        list.add(child);
        if (compound.getInt("Int", -1) != 37 || !"fuream".equals(compound.getString("String")) ||
            Math.abs(compound.getFloat("Float", 0f) - 1.25f) > 0.0001f ||
            list.size() != 1 || list.getCompound(0).getInt("Value", -1) != 9) {
            throw new IllegalStateException("Native NBT primitive/list round trip failed");
        }

        ItemStack plain = new ItemStack(Material.STONE, 3);
        assertItemRoundTrip(plain, false);
        ItemStack named = plain.clone();
        ItemMeta meta = named.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Fuream NBT self-test");
            named.setItemMeta(meta);
            assertItemRoundTrip(named, true);
        }
    }

    private static void assertItemRoundTrip(ItemStack expected, boolean checkName) {
        ItemStack actual = NativeItemNbt.read(NativeItemNbt.write(expected));
        if (actual.getType() != expected.getType() || actual.getAmount() != expected.getAmount()) {
            throw new IllegalStateException("Native item NBT round trip changed the item");
        }
        if (checkName && (!actual.hasItemMeta() || !actual.getItemMeta().hasDisplayName() ||
            !expected.getItemMeta().getDisplayName().equals(actual.getItemMeta().getDisplayName()))) {
            throw new IllegalStateException("Native item NBT round trip lost item metadata");
        }
    }

    private NbtSelfTest() {
    }
}
