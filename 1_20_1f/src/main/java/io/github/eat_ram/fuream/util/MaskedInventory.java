package io.github.eat_ram.fuream.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Predicate;

import io.github.eat_ram.fuream.CollectionUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class MaskedInventory extends DefaultedList<@NotNull ItemStack> {
    public final @NotNull MaskedInventoryDelegate inventoryDelegate;

    public MaskedInventory(
        @NotNull InventoryViewProfile @NotNull... inventories
    ) {
        this(new MaskedInventoryDelegate(inventories));
    }

    private MaskedInventory(@NotNull MaskedInventoryDelegate delegate) {
        super(delegate, ItemStack.EMPTY);
        this.inventoryDelegate = delegate;
    }

    public static class InventoryViewProfile {
        public final @NotNull List<@NotNull ItemStack> inventory;
        public final @NotNull Iterable<
            ? extends @NotNull Predicate<? super ItemStack>
        > filters;
        public final boolean accumulating;

        public InventoryViewProfile(
            @NotNull List<@NotNull ItemStack> inventory,
            @NotNull Iterable<
                ? extends @NotNull Predicate<? super ItemStack>
            > filters, boolean accumulating
        ) {
            this.inventory = inventory;
            this.filters = filters;
            this.accumulating = accumulating;
        }

        public InventoryViewProfile(
            @NotNull List<@NotNull ItemStack> inventory,
            @NotNull Predicate<? super ItemStack> filter, boolean accumulating
        ) {
            this.inventory = inventory;
            this.filters = Collections.singleton(filter);
            this.accumulating = accumulating;
        }

        public InventoryViewProfile(
            @NotNull List<@NotNull ItemStack> inventory, boolean accumulating
        ) {
            this(inventory, stack -> !stack.isEmpty(), accumulating);
        }
    }

    public static class MaskedInventoryDelegate
    implements List<@NotNull ItemStack> {
        private final @NotNull InventoryViewProfile @NotNull[] inventories;

        @Contract(pure = true)
        public MaskedInventoryDelegate(
            @NotNull InventoryViewProfile @NotNull... inventories
        ) {
            this.inventories = inventories;
        }

        @Contract(pure = true)
        public @NotNull InventoryViewProfile getProfile(int index) {
            return this.inventories[index];
        }

        @Override
        @Contract(pure = true)
        public int size() {
            return this.inventories.length;
        }

        @Override
        public boolean isEmpty() {
            return this.inventories.length == 0;
        }

        @Override
        public boolean contains(Object o) {
            for (int i = 0; i < this.inventories.length; ++i) {
                if (this.get(i).equals(o)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public @NotNull Iterator<ItemStack> iterator() {
            return CollectionUtil.listIterator(this, this.size(), -1);
        }

        @Override
        public @NotNull Object @NotNull[] toArray() {
            Object[] a = new Object[this.inventories.length];
            for (int i = 0; i < this.inventories.length; ++i) {
                a[i] = this.get(i);
            }
            return a;
        }

        @Override
        @SuppressWarnings("unchecked")
        public @NotNull <T> T @NotNull[] toArray(T @NotNull[] a) {
            if (a.length < this.inventories.length) {
                return (T[])this.toArray();
            }
            for (int i = 0; i < this.inventories.length; ++i) {
                a[i] = (T)this.get(i);
            }
            return a;
        }

        @Override
        public boolean add(ItemStack stack) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            for (Object o : c) {
                if (!this.contains(o)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends ItemStack> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int index, Collection<? extends ItemStack> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NotNull ItemStack get(int index) {
            for (Predicate<? super ItemStack> filter :
                 this.inventories[index].filters) {
                for (ItemStack stack : this.inventories[index].inventory) {
                    if (filter.test(stack)) {
                        return stack;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack set(int index, @NotNull ItemStack element) {
            InventoryViewProfile profile = this.inventories[index];
            if (profile.accumulating) {
                int i = 0;
                for (ItemStack stack : profile.inventory) {
                    if (stack.isEmpty()) {
                        return profile.inventory.set(i, element);
                    }
                    ++i;
                }
                return i > 0 ? profile.inventory.set(i - 1, element) :
                       ItemStack.EMPTY;
            }
            int i = 0;
            for (Predicate<? super ItemStack> filter : profile.filters) {
                i = 0;
                for (ItemStack stack : profile.inventory) {
                    if (filter.test(stack)) {
                        return profile.inventory.set(i, element);
                    }
                    ++i;
                }
            }
            return i > 0 ? profile.inventory.set(0, element) : ItemStack.EMPTY;
        }

        @Override
        public void add(int index, ItemStack element) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemStack remove(int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < this.inventories.length; ++i) {
                if (this.get(i).equals(o)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = this.inventories.length - 1; i >= 0; --i) {
                if (this.get(i).equals(o)) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public @NotNull ListIterator<@NotNull ItemStack> listIterator() {
            return CollectionUtil.listIterator(this, this.size(), 0);
        }

        @Override
        public @NotNull ListIterator<@NotNull ItemStack> listIterator(int index) {
            return CollectionUtil.listIterator(this, this.size(), index);
        }

        @Override
        public @NotNull List<@NotNull ItemStack>
        subList(int fromIndex, int toIndex) {
            if (fromIndex >= toIndex || this.size() == 0) {
                return Collections.emptyList();
            }
            InventoryViewProfile[] profiles =
            new InventoryViewProfile[toIndex - fromIndex];
            int ni = 0;
            for (int i = fromIndex; i < toIndex; ++i) {
                profiles[ni] = this.inventories[i];
                ++ni;
            }
            return new MaskedInventory(profiles);
        }
    }
}
