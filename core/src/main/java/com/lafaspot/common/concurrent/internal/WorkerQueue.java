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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import javax.annotation.concurrent.ThreadSafe;

import com.lafaspot.common.concurrent.WorkerException;

/**
 * This class keeps references to a worker until it is picked by a WorkerManager thread. It also has references to all current futures.
 *
 * @author lafa
 *
 */
@SuppressWarnings("rawtypes")
@ThreadSafe
public class WorkerQueue {
    /** Queue of workers. */
    private final Queue<WorkerWrapper> queue = new ConcurrentLinkedQueue<WorkerWrapper>();
    /** List of futures for the workers in the queue. */
    private final List<Future<WorkerManagerState>> futures = Collections.synchronizedList(new ArrayList<Future<WorkerManagerState>>());

    /**
     * Returns and removes the next worker from the queue.
     *
     * @return the next WorkerWrapper instance from the queue
     */
    protected WorkerWrapper getWorker() {
        return queue.poll();
    }

    /**
     * Adds the given worker to the queue.
     *
     * @param worker worker instance to add
     * @throws WorkerException if the add failed
     */
    public void add(final WorkerWrapper worker) throws WorkerException {
        if (!queue.offer(worker)) {
            throw new WorkerException("Failed to add the worker to the queue.");
        }
    }

    /**
     * @return true if the queue is empty, else false
     */
    protected boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Used for determining if the given Future is done.
     *
     * @author lafa
     */
    private class FutureDone implements Predicate<Future<WorkerManagerState>> {
        @Override
        public boolean test(final Future<WorkerManagerState> future) {
            return future.isDone();
        }
    }

    /**
     * This method not be called very often since it blocks all threads for remove and addition.
     *
     * @param future the future instance
     */
    public void addFuture(final Future<WorkerManagerState> future) {
        futures.add(future);
    }

    /**
     * @return the number of futures in the list
     */
    public int futureSize() {
        return futures.size();
    }

    /**
     * Removes all futures that are done from the list.
     */
    public void removeFuturesDone() {
        futures.removeIf(new FutureDone());
    }

}