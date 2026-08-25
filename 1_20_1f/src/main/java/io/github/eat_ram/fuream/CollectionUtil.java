package io.github.eat_ram.fuream;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Range;

public abstract class CollectionUtil {
    @Contract(value = "_, _ -> new", pure = true)
    public static <E> ArrayList<E> newDefaultedArrayList(
        E defaultValue,
        @Range(from = 0, to = Integer.MAX_VALUE) int initialSize
    ) {
        ArrayList<E> al = new ArrayList<>() {
            @Override
            @Contract(pure = true)
            public E get(int index) {
                return index < this.size() ? super.get(index) : defaultValue;
            }

            @Override
            public E set(int index, E element) {
                int size = this.size();
                if (index < size) {
                    return super.set(index, element);
                }
                this.ensureCapacity(index + 1);
                for (; size < index; ++size) {
                    this.add(defaultValue);
                }
                this.add(element);
                return defaultValue;
            }
        };
        if (initialSize > 0) {
            al.set(initialSize - 1, defaultValue);
        }
        return al;
    }

    @Contract(value = "_, _, _ -> new", pure = true)
    public static <E> ListIterator<E>
    listIterator(List<E> list, int size, int index) {
        return new ListIterator<>() {
            public final boolean isListIterator = index >= 0;
            private int cursor = index < 0 ? 0 : index;

            @Contract(pure = true)
            public boolean hasNext() {
                return this.cursor != size;
            }

            public E next() {
                try {
                    int i = this.cursor;
                    E next = list.get(i);
                    this.cursor = i + 1;
                    return next;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            @Contract("-> fail")
            public void remove() {
                throw new UnsupportedOperationException();
            }

            public boolean hasPrevious() {
                if (!this.isListIterator) {
                    throw new UnsupportedOperationException();
                }
                return this.cursor != 0;
            }

            public E previous() {
                if (!this.isListIterator) {
                    throw new UnsupportedOperationException();
                }
                try {
                    int i = this.cursor - 1;
                    E previous = list.get(i);
                    this.cursor = i;
                    return previous;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            public int nextIndex() {
                if (!this.isListIterator) {
                    throw new UnsupportedOperationException();
                }
                return this.cursor;
            }

            public int previousIndex() {
                if (!this.isListIterator) {
                    throw new UnsupportedOperationException();
                }
                return this.cursor - 1;
            }

            @Contract("_ -> fail")
            public void set(E e) {
                throw new UnsupportedOperationException();
            }

            @Contract("_ -> fail")
            public void add(E e) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Contract("-> fail")
    private CollectionUtil() {
        throw new UnsupportedOperationException();
    }
}
