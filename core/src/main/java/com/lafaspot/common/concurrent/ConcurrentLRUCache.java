/*
 * Copyright [yyyy] [name of copyright owner]
 * 
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  ====================================================================
 */
package com.lafaspot.common.concurrent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Thread safe Least Recently Used cache. The implementation allows for a LRU cache logic, but the default is to evict the elements in FIFO order.
 * This reduces the computation and improves reading performance form the cache.
 *
 * To implement the LRU logic it is necessary to call method touch(key) or call method put(key, value) with the same [key, value] pair.
 *
 * maxSize is an approximation.
 *
 *
 * @param <Key>
 * @param <Value>
 */
public class ConcurrentLRUCache<Key, Value> {
    /** MISS count. */
    private static final int MISS = 0;
    /** HIT count. */
    private static final int HIT = 1;
    /** REMOVED_KEYS count. */
    private static final int REMOVED_KEYS = 2;
    /** REUSED_KEYS count. */
    private static final int REUSED_KEYS = 3;
    /** mMaxSize count. */
    private final int mMaxSize;
    /** map variable. */
    private final ConcurrentHashMap<Key, Value> map;
    /** queue variable. */
    private final ConcurrentLinkedQueue<Key> queue;

    /** Stats. */
    private final AtomicLongArray stats = new AtomicLongArray(4);

    /**
     * @param maxSize max permitted size
     */
    public ConcurrentLRUCache(final int maxSize) {
        mMaxSize = maxSize;
        map = new ConcurrentHashMap<Key, Value>(maxSize);
        queue = new ConcurrentLinkedQueue<Key>();
    }

    /**
     * This constructor can be used to clone, reduce or increase the cache size.
     *
     * @param maxSize max npermitted size
     * @param cache concurrentLRUCache
     */
    public ConcurrentLRUCache(final int maxSize, final ConcurrentLRUCache<Key, Value> cache) {
        this(maxSize);
        for (final Key key : cache.queue) {
            final Value value = cache.get(key);
            if (null != value) {
                this.put(key, value);
            }
        }
    }

    /**
     * Touch a element in the cache, marks the element as recently used. On a single thread maxSize will not be exceeded.
     *
     * @param key - null key is not supported
     */
    public void touch(final Key key) {
        if (map.containsKey(key)) {
            synchronized (this) {
                // update queue age for passed key
                queue.remove(key);
                queue.add(key);
                stats.incrementAndGet(REUSED_KEYS);
            }
        }
    }

    /**
     * Insert a element in the cache. The tries to use the maxSize as an approximation, the cache can grow a bit more than maxSize. The higher
     * concurrency on puts, the higher the probability of maxSize will be exceeded. On a single thread maxSize will not be exceeded.
     *
     * @param key - null key is not supported
     * @param val value Object
     */
    public void put(final Key key, final Value val) {
        if (map.containsKey(key)) {
            synchronized (this) {
                // update queue age for passed key
                queue.remove(key);
                queue.add(key);
                map.put(key, val);
                stats.incrementAndGet(REUSED_KEYS);
            }
            return;
        }

        while (map.size() >= mMaxSize) {
            synchronized (this) {
                final Key oldestKey = queue.poll();
                if (null != oldestKey) {
                    map.remove(oldestKey);
                    stats.incrementAndGet(REMOVED_KEYS);
                }
            }
        }

        synchronized (this) {
            if (map.put(key, val) == null) {
                // add to queue if it didn't exist earlier
                queue.add(key);
            } else {
                // update the age if the key was present
                queue.remove(key);
                queue.add(key);
            }
        }
    }

    /**
     * Remove a key from the cache.
     *
     * @param key the key used to lookup cached value
     */
    public void remove(final Key key) {
        if (map.containsKey(key)) {
            synchronized (this) {
                queue.remove(key);
                map.remove(key);
                stats.incrementAndGet(REMOVED_KEYS);
            }
        }
    }

    /**
     * Retrieve a value from cache.
     *
     * @param key - null key is not supported
     * @return key value
     */
    public Value get(final Key key) {
        final Value v = map.get(key);
        if (v == null) {
            stats.incrementAndGet(MISS);
        } else {
            stats.incrementAndGet(HIT);
        }
        return v;
    }

    /**
     * Retrieve the max size.
     *
     * @return maxsize
     */
    public int getMaxSize() {
        return mMaxSize;
    }

    /**
     * Retrieve current size.
     *
     * @return size
     */
    public int size() {
        synchronized (this) {
            return map.size();
        }
    }

    /**
     * clear cache.
     *
     */
    public void clear() {
        synchronized (this) {
            queue.clear();
            map.clear();
            // reset stats
            for (int i = 0; i < stats.length(); i++) {
                stats.set(i, 0);
            }
        }
    }

    /**
     * Returns a string with the LRU stats. This method is for testing only, should be used as part of the api.
     *
     * @return - a string with stats on the cache
     */
    public String getStats() {
        return "SIZE:" + map.size() + ", MAX_SIZE:" + mMaxSize + ", HIT:" + stats.get(HIT) + ", MISS:" + stats.get(MISS) + ", REUSED_KEYS:"
                + stats.get(REUSED_KEYS) + ", REMOVED_KEYS:" + stats.get(REMOVED_KEYS);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getStats();
    }
}
