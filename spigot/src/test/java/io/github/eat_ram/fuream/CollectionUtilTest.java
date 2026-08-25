package io.github.eat_ram.fuream;

import java.util.ArrayList;
import java.util.ListIterator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CollectionUtilTest {
    @Test
    public void testDefaultedArrayList() {
        ArrayList<String> list = CollectionUtil.newDefaultedArrayList("EMPTY", 5);
        assertEquals(5, list.size());
        assertEquals("EMPTY", list.get(0));
        assertEquals("EMPTY", list.get(4));
        assertEquals("EMPTY", list.get(10)); // Out of bounds returns default

        list.set(2, "ITEM_2");
        assertEquals("ITEM_2", list.get(2));

        list.set(7, "ITEM_7");
        assertEquals(8, list.size());
        assertEquals("ITEM_7", list.get(7));
        assertEquals("EMPTY", list.get(6));
    }

    @Test
    public void testListIterator() {
        ArrayList<String> list = CollectionUtil.newDefaultedArrayList("AIR", 3);
        list.set(0, "A");
        list.set(1, "B");
        list.set(2, "C");

        ListIterator<String> it = CollectionUtil.listIterator(list, 3, 0);
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());

        assertTrue(it.hasPrevious());
        assertEquals("C", it.previous());
        assertEquals("B", it.previous());
        assertEquals("A", it.previous());
        assertFalse(it.hasPrevious());
    }
}
