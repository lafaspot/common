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

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 *
 * concurrent lru cache test.
 */
public class ConcurrentLRUCacheTest {

    /** true. */
    private static Boolean mTrue = true;
    /** false. */
    private static Boolean mFalse = false;

    /**
     * test lru cache.
     */
    @Test
    public void testLRUCache() {
        final ConcurrentLRUCache<String, Boolean> cache = new ConcurrentLRUCache<String, Boolean>(3);
        Boolean value = cache.get("true");
        Assert.assertNull(value);
        cache.put("true", mTrue);
        value = cache.get("true");
        Assert.assertEquals(value, mTrue);
        cache.put("true", mFalse);
        value = cache.get("true");
        Assert.assertNotSame(value, mTrue);
    }

    /**
     * test lru cache resize clone.
     */
    @Test
    public void testLRUCacheReSizeClone() {
        final ConcurrentLRUCache<String, Boolean> cache3 = new ConcurrentLRUCache<String, Boolean>(3);

        cache3.put("value1", mTrue);
        cache3.put("value2", mFalse);
        cache3.put("value3", mFalse);

        final ConcurrentLRUCache<String, Boolean> cache3new = new ConcurrentLRUCache<String, Boolean>(3, cache3);

        cache3new.put("value4", mFalse); // over size
        Assert.assertEquals(cache3new.get("value1"), null);

        cache3new.put("value3", mFalse); // add same key
        Assert.assertEquals(cache3new.get("value2"), mFalse);

        cache3new.touch("value2");
        cache3new.put("value5", mFalse); // add new key

        // value2 is still on the cache since it was touched
        Assert.assertEquals(cache3new.get("value2"), mFalse);
        // value4 was the oldest value in the cache
        Assert.assertEquals(cache3new.get("value4"), null);

        Assert.assertEquals(cache3new.size(), 3);

        // Test clear cache
        cache3new.clear();

        Assert.assertEquals(cache3new.size(), 0);
    }

    /**
     * test LRUCacheReSizeReduce.
     */
    @Test
    public void testLRUCacheReSizeReduce() {
        final ConcurrentLRUCache<String, Boolean> cache3 = new ConcurrentLRUCache<String, Boolean>(3);

        cache3.put("value1", mTrue);
        cache3.put("value2", mFalse);
        cache3.put("value3", mFalse);

        final ConcurrentLRUCache<String, Boolean> cache2 = new ConcurrentLRUCache<String, Boolean>(2, cache3);

        Assert.assertEquals(cache2.size(), 2);

        Assert.assertEquals(cache2.get("value1"), null);

        // Test clear cache
        cache2.clear();

        Assert.assertEquals(cache2.size(), 0);
    }

    /**
     * test LRUCacheReSizeIncrease.
     */
    @Test
    public void testLRUCacheReSizeIncrease() {
        final ConcurrentLRUCache<String, Boolean> cache3 = new ConcurrentLRUCache<String, Boolean>(3);

        cache3.put("value1", mTrue);
        cache3.put("value2", mFalse);
        cache3.put("value3", mFalse);

        final ConcurrentLRUCache<String, Boolean> cache5 = new ConcurrentLRUCache<String, Boolean>(5, cache3);

        cache5.put("value4", mFalse); // over size
        cache5.put("value3", mFalse); // add same key
        cache5.touch("value2");
        cache5.put("value5", mFalse); // add new key

        // value2 is still on the cache since it was touched
        Assert.assertEquals(cache5.get("value2"), mFalse);
        // value4 was the oldest value in the cache
        Assert.assertEquals(cache5.get("value4"), mFalse);

        Assert.assertEquals(cache5.size(), 5);

        // Test clear cache
        cache5.clear();

        Assert.assertEquals(cache5.size(), 0);
    }

    /**
     * test LRUCacheSize.
     */
    @Test
    public void testLRUCacheSize() {
        final ConcurrentLRUCache<String, Boolean> cache = new ConcurrentLRUCache<String, Boolean>(3);

        cache.put("value1", mTrue);
        cache.put("value2", mFalse);
        cache.put("value3", mFalse);
        cache.put("value4", mFalse); // over size
        cache.put("value3", mFalse); // add same key
        cache.touch("value2");
        cache.put("value5", mFalse); // add new key

        // value2 is still on the cache since it was touched
        Assert.assertEquals(cache.get("value2"), mFalse);
        // value4 was the oldest value in the cache
        Assert.assertEquals(cache.get("value4"), null);

        Assert.assertEquals(cache.size(), 3);

        // Test clear cache
        cache.clear();

        Assert.assertEquals(cache.size(), 0);
    }

    /**
     * test LRUCacheRemove.
     */
    @Test
    public void testLRUCacheRemove() {
        final ConcurrentLRUCache<String, Boolean> cache = new ConcurrentLRUCache<String, Boolean>(3);

        cache.put("value1", mTrue);
        cache.put("value2", mFalse);
        cache.remove("value1");
        Assert.assertEquals(cache.get("value1"), null);
        // add 2 more elements
        cache.put("value3", mTrue);
        cache.put("value4", mFalse);
        // ensure all elements are present
        Assert.assertEquals(cache.get("value2"), mFalse);
        Assert.assertEquals(cache.get("value3"), mTrue);
        Assert.assertEquals(cache.get("value4"), mFalse);
    }

    /** macache. */
    private ConcurrentLRUCache<String, Boolean> mCache = new ConcurrentLRUCache<String, Boolean>(50);

    /**
     * test lru concurrency.
     */
    @Test(threadPoolSize = 30, invocationCount = 32, invocationTimeOut = 10000)
    public void testLRUconcurency() {
        final String tid = "" + Thread.currentThread().getId();

        for (int i = 0; i <= 100; i++) {
            mCache.put(tid + "value" + i, mTrue);
            mCache.put("value" + i, mFalse);
        }

        for (int i = 0; i <= 100; i++) {
            final Boolean value = mCache.get(tid + "value" + i);
            if (value != null) {
                Assert.assertEquals(value, mTrue);
            } else {
                final int size = mCache.size();
                Assert.assertTrue(size >= 20, "size=" + size + ". size should always be large than 48.");
                Assert.assertTrue(size <= 80, "size=" + size + ". Size should always smaller the maxSize + maxthreads.");
            }
        }

        final int size = mCache.size();
        Assert.assertTrue(size >= 20, "size=" + size + ". size should always be large than 48.");
        Assert.assertTrue(size <= 80, "size=" + size + ". Size should always smaller the maxSize + maxthreads.");
    }

    /**
     * test zlru concurrency.
     */
    @Test
    public void testZLRUconcurency() {
        Assert.assertTrue(mCache.getStats().contains("HIT:"), "stats validation");
    }

    /**
     * A load test to measure the performance of insertion to LRU Cache. This is currently disabled and MUST be run manually everytime the cache code
     * is updated.
     *
     * The time taken by the test will vary from machine to machine. On the machine that this was originally tested, the for loop took 169ms. We
     * should expect to see numbers in that range.
     */
    @Test(enabled = false)
    public void testPerformance() {
        final int count = 100000;
        final ConcurrentLRUCache<String, Boolean> cache = new ConcurrentLRUCache<String, Boolean>(count);

        final long startTime = System.currentTimeMillis();
        for (int i = 0; i <= count; ++i) {
            cache.put("value" + i, true);
        }
        final long putFinishTime = System.currentTimeMillis();
        Assert.assertTrue((putFinishTime - startTime) <= 200);

        for (int i = 0; i <= count; ++i) {
            cache.get("value" + i);
        }
        final long lookupFinishTime = System.currentTimeMillis();
        Assert.assertTrue((lookupFinishTime - putFinishTime) <= 100);
    }

}
