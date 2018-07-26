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

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lafaspot.common.concurrent.WorkerBlockManager;
import com.lafaspot.common.concurrent.WorkerExecutorService;

/**
 * Internal Class used by WorkerExecutor Service, to manage workers in a safe reliable way.
 *
 * @author lafa
 */
@SuppressWarnings("rawtypes")
@NotThreadSafe
public class WorkerManagerOneThread implements Callable<WorkerManagerState> {
    /** Executor service instance. If shutdown, this thread will exit. */
    private final WorkerExecutorService executorService;
    /** Internal list of workers to process in this thread. */
    private final LinkedList<WorkerWrapper> workers;
    /** Index for iterating over for our list of workers to be processed. */
    private ListIterator<WorkerWrapper> workerIter;
    /** Reference to the global worker queue from which we will retrieve additional workers to add to our internal list. */
    private final WorkerQueue workerSourceQueue;
    /** Logger instance. */
    private final Logger logger = LoggerFactory.getLogger(WorkerManagerOneThread.class);
    /** Time in milliseconds we will keep this thread running while there are no workers to be processed. */
    private static final int THREAD_IDLE_WAIT_TIME_MILLIS = 30000;
    /** Time in nanoseconds that the thread will sleep between calling the workers. */
    private static final int TIME_INTERVAL_PER_EXECUTE_CALL_NANOS = 1000000;
    /** Time in milliseconds after which we will log an error indicating that a worker took too long to return from a call to execute. */
    private static final long WORKER_EXECUTE_WARN_TIME_MILLIS = 1000;
    /** Time in milliseconds that the thread will sleep when count reach SLEEP_COUNT_NANO. */
    private static final int SLEEP_TIME_MILLIS = 1;
    /** When sleepCount reach SLEEP_COUNT_NANO, than sleep. */
    private static final int SLEEP_COUNT_NANO = 999999;
    /** Sleep buffer factor add between each execute. */
    private static final int SLEEP_BUFFER_FACTOR = 50000;

    /**
     * Creates an instance of this callable for the given executor and worker queue.
     *
     * @param executor the executor instance to which this object will be submitted
     * @param queue the WorkerQueue from which this thread should retrieve workers
     */
    public WorkerManagerOneThread(@Nonnull final WorkerExecutorService executor, @Nonnull final WorkerQueue queue) {
        executorService = executor;
        workers = new LinkedList<WorkerWrapper>();
        workerIter = workers.listIterator();
        workerSourceQueue = queue;
    }

    /**
     * This method loops until it has no workers to process for some period of time, at which point the thread will exit. Each iteration of the loop
     * will pull a worker from the shared worker pool and a worker from this manager's internal queue and execute them. If the worker from the shared
     * pool is not done with its work after the first call, it will be added to the internal queue. This algorithm assures that new workers in the
     * shared pool will have a high priority so their work (which should be asynchronous) can be started quickly.
     *
     * @return WorkerManagerState object
     * @throws Exception if an error occurs processing the workers
     */
    @Override
    public WorkerManagerState call() throws Exception {
        long doneSince = 0;
        boolean done = false;
        int sleep = 0;
        long loopStartTime = System.nanoTime();
        long loopTotalSleepTime = 0;

        while (!done && !executorService.isShutdown()) {
            final WorkerWrapper existingWorker;
            final WorkerWrapper newWorker = workerSourceQueue.getWorker();

            if (workerIter.hasNext()) {
                existingWorker = workerIter.next();
            } else {
                // We reached the end of our queue, reset the iterator.
                analyzeStats(workers, loopStartTime, loopTotalSleepTime);
                loopStartTime = System.nanoTime();
                loopTotalSleepTime = 0;
                workerIter = workers.listIterator();
                if (workerIter.hasNext()) {
                    existingWorker = workerIter.next();
                } else {
                    existingWorker = null;
                }
            }

            boolean addNewWorkerToList = false;
            boolean newWorkerExitedWithTrue = false;
            if (newWorker != null) {
                // Execute the new worker first, and if it's not complete add it to our queue
                WorkerBlockManager blockManager = newWorker.getWorkerImpl().getBlockManager();
                try {
                    blockManager.enterExecuteCall();
                    if (!newWorker.execute()) {
                        addNewWorkerToList = true;
                    } else {
                        newWorkerExitedWithTrue = true;
                    }
                } finally {
                    blockManager.exitExecuteCall(newWorkerExitedWithTrue);
                }
            }

            boolean existingWorkerExitedWithTrue = false;
            if (existingWorker != null) {
                WorkerBlockManager blockManager = existingWorker.getWorkerImpl().getBlockManager();
                try {
                    blockManager.enterExecuteCall();
                    if (existingWorker.execute()) {
                        // This worker is done, so remove it from our internal queue
                        workerIter.remove();
                        existingWorkerExitedWithTrue = true;
                    }
                } finally {
                    blockManager.exitExecuteCall(existingWorkerExitedWithTrue);
                }
            }

            if (addNewWorkerToList) {
                workerIter.add(newWorker);
            }

            if (existingWorker == null && newWorker == null) {
                // Wait for 100ms before releasing the thread
                if (doneSince == 0) {
                    doneSince = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - doneSince > THREAD_IDLE_WAIT_TIME_MILLIS) {
                    done = true;
                }
            } else {
                doneSince = 0;
            }

            // If a worker just exited with true, don't sleep.
            if (newWorkerExitedWithTrue || existingWorkerExitedWithTrue) {
                continue;
            } else {
                final int workersSize = workers.size();
                if (workersSize <= 1) {
                    sleep = 0;
                    loopTotalSleepTime += SLEEP_TIME_MILLIS;
                    Thread.sleep(SLEEP_TIME_MILLIS);
                } else {
                    // 50000 nanoseconds for buffer then calculate timePerIteration = TIME_INTERVAL_PER_EXECUTE_CALL_NANOS / (workers.size() + 1)
                    // to sleep at least TIME_INTERVAL_PER_EXECUTE_CALL_NANOS before calling the same worker
                    sleep += SLEEP_BUFFER_FACTOR + TIME_INTERVAL_PER_EXECUTE_CALL_NANOS / workersSize;
                }
            }

            if (sleep >= SLEEP_COUNT_NANO) {
                sleep = 0;
                loopTotalSleepTime += SLEEP_TIME_MILLIS;
                Thread.sleep(SLEEP_TIME_MILLIS);
            }
        }
        workerSourceQueue.removeFuturesDone();
        return new WorkerManagerState(workers);
    }

    /**
     * Analyzes stats for all the workers in this WorkerManagerOneThreads's queue.
     *
     * @param workers the worker's queue
     * @param loopStartTime the start time for looping through the worker's queue
     * @param loopTotalSleepTime the total time this WorkerManager slept executing the worker's queue
     */
    private void analyzeStats(@Nonnull final LinkedList<WorkerWrapper> workers, final long loopStartTime, final long loopTotalSleepTime) {
        if (!logger.isDebugEnabled()) {
            return;
        }

        long loopDuration = System.nanoTime() - loopStartTime;
        if (loopDuration <= (2 * SLEEP_COUNT_NANO)) {
            return;
        }

        final StringBuffer sb = new StringBuffer();
        sb.append("WorkerLoopTotalSleepTime=").append(loopTotalSleepTime).append("ms, WorkerLoopTotalDuration=").append(loopDuration)
                .append("nanos, WorkersCount=").append(workers.size()).append(", Workers=[");
        for (final WorkerWrapper wrapper : workers) {
            sb.append(wrapper.getStat().getStatsAsString()).append(" ");
        }
        sb.append("]");
        logger.debug(sb.toString());
    }

}
