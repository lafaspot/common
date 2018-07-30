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
 * Class to test @code Workerstats.
 *
 */
public class BlockIdStatsTest {

    /**
     * Method to test the default values.
     */
    @Test
    public void testConstructorValues() {
        BlockIdStats workerStats = new BlockIdStats(3L, 2L, 4L);
        Assert.assertEquals(workerStats.getCount(), 3, "count is expected to be 3");
        Assert.assertEquals(workerStats.getInQueue(), 4, "inQueue is expected to be 4");
        Assert.assertEquals(workerStats.getUnblocked(), 2, "unblocked is expected to be 2");
    }
}
