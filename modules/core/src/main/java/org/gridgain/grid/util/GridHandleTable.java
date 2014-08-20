/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.util;

import sun.misc.*;

import java.util.*;

/**
 * Lightweight identity hash table which maps objects to integer handles,
 * assigned in ascending order.
 */
public class GridHandleTable {
    /** */
    private static final Unsafe UNSAFE = GridUnsafe.unsafe();

    /** */
    private static final long intArrOff = UNSAFE.arrayBaseOffset(int[].class);

    /** Number of mappings in table/next available handle. */
    private int size;

    /** Size threshold determining when to expand hash spine. */
    private int threshold;

    /** Factor for computing size threshold. */
    private final float loadFactor;

    /** Maps hash value -> candidate handle value. */
    private int[] spine;

    /** Maps handle value -> next candidate handle value. */
    private int[] next;

    /** Maps handle value -> associated object. */
    private Object[] objs;

    /** */
    private int[] spineEmpty;

    /** */
    private int[] nextEmpty;

    /**
     * Creates new HandleTable with given capacity and load factor.
     *
     * @param initCap Initial capacity.
     * @param loadFactor Load factor.
     */
    public GridHandleTable(int initCap, float loadFactor) {
        this.loadFactor = loadFactor;

        spine = new int[initCap];
        next = new int[initCap];
        objs = new Object[initCap];
        spineEmpty = new int[initCap];
        nextEmpty = new int[initCap];

        Arrays.fill(spineEmpty, -1);
        Arrays.fill(nextEmpty, -1);

        threshold = (int)(initCap * loadFactor);

        clear();
    }

    /**
     * Looks up and returns handle associated with given object, or -1 if
     * no mapping found.
     *
     * @param obj Object.
     * @return Handle.
     */
    public int lookup(Object obj) {
        int idx = hash(obj) % spine.length;

        if (size > 0) {
            for (int i = spine[idx]; i >= 0; i = next[i])
                if (objs[i] == obj)
                    return i;
        }

        if (size >= next.length)
            growEntries();

        if (size >= threshold)
            growSpine();

        insert(obj, size, idx);

        size++;

        return -1;
    }

    /**
     * Resets table to its initial (empty) state.
     */
    public void clear() {
        UNSAFE.copyMemory(spineEmpty, intArrOff, spine, intArrOff, spineEmpty.length << 2);
        UNSAFE.copyMemory(nextEmpty, intArrOff, next, intArrOff, nextEmpty.length << 2);

        Arrays.fill(objs, null);

        size = 0;
    }

    /**
     * @return Returns objects that were added to handles table.
     */
    public Object[] objects() {
        return objs;
    }

    /**
     * Inserts mapping object -> handle mapping into table. Assumes table
     * is large enough to accommodate new mapping.
     *
     * @param obj Object.
     * @param handle Handle.
     * @param idx Index.
     */
    private void insert(Object obj, int handle, int idx) {
        objs[handle] = obj;
        next[handle] = spine[idx];
        spine[idx] = handle;
    }

    /**
     * Expands the hash "spine" - equivalent to increasing the number of
     * buckets in a conventional hash table.
     */
    private void growSpine() {
        int size = (spine.length << 1) + 1;

        spine = new int[size];
        spineEmpty = new int[size];
        threshold = (int)(spine.length * loadFactor);

        Arrays.fill(spineEmpty, -1);

        UNSAFE.copyMemory(spineEmpty, intArrOff, spine, intArrOff, spineEmpty.length << 2);

        for (int i = 0; i < this.size; i++) {
            Object obj = objs[i];

            int idx = hash(obj) % spine.length;

            insert(objs[i], i, idx);
        }
    }

    /**
     * Increases hash table capacity by lengthening entry arrays.
     */
    private void growEntries() {
        int newLen = (next.length << 1) + 1;
        int[] newNext = new int[newLen];

        UNSAFE.copyMemory(next, intArrOff, newNext, intArrOff, size << 2);

        next = newNext;
        nextEmpty = new int[newLen];

        Arrays.fill(nextEmpty, -1);

        Object[] newObjs = new Object[newLen];

        System.arraycopy(objs, 0, newObjs, 0, size);

        objs = newObjs;
    }

    /**
     * Returns hash value for given object.
     *
     * @param obj Object.
     * @return Hash value.
     */
    private int hash(Object obj) {
        return System.identityHashCode(obj) & 0x7FFFFFFF;
    }
}
