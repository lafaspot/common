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
package com.lafaspot.common.concurrent.internal;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.lafaspot.common.concurrent.Worker;

/**
 * Unit tests for {@link WorkerStats}.
 *
 * @author altafh
 *
 */
public class WorkerStatsTest {

    /**
     * Tests stats operation for {@link WorkerStats}.
     *
     */
    @Test
    public void testWorkerStats() {
        final WorkerStats stats = new WorkerStats(Worker.class);
        stats.recordBeginExecute();
        stats.recordEndExecute(true);
        stats.recordCanceled();
        stats.recordExceptionTriggered(new IOException());
        final String workerStatsString = stats.getStatsAsString();
        Assert.assertNotNull(workerStatsString);
    }
}
