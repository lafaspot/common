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

/**
 * Class which stores the Worker stats count. It is populated from @code BlockManagerMaxCount.BlockIdState.
 *
 * @author deekshav
 *
 */
public class BlockIdStats {

    /** Number of workers trying to run currently. */
    private final long count;
    /** Number of workers currently running. */
    private final long unblocked;
    /** Total number of workers. */
    private final long inQueue;

    /**
     * Method to get the count value.
     *
     * @return count
     */
    public long getCount() {
        return count;
    }

    /**
     * Method to get the unblocked values.
     *
     * @return unblocked
     */
    public long getUnblocked() {
        return unblocked;
    }

    /**
     * Method to get the InQueue value.
     *
     * @return inQueue.
     */
    public long getInQueue() {
        return inQueue;
    }

    /**
     * Constructor.
     *
     * @param count value
     * @param unblocked value
     * @param inQueue value
     */
    public BlockIdStats(final long count, final long unblocked, final long inQueue) {
        super();
        this.count = count;
        this.unblocked = unblocked;
        this.inQueue = inQueue;
    }

}
