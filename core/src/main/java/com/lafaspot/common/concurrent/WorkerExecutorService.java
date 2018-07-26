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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.ThreadSafe;

import com.lafaspot.common.concurrent.internal.ExecutorThreadFactory;
import com.lafaspot.common.concurrent.internal.WorkerManagerOneThread;
import com.lafaspot.common.concurrent.internal.WorkerManagerState;
import com.lafaspot.common.concurrent.internal.WorkerQueue;
import com.lafaspot.common.concurrent.internal.WorkerWrapper;

/**
 * Implements a generic executor Service, where class that implement the interface worker can be submitted for execution. <code>
 * WorkerExecutorService executor = new WorkerExecutorService();
 * WorkerFuture<String> future = executor.submit(new ReverseStringWorker("12345"));
 * String reversedString = future.get();
 * executor.shutdown();
 * </code>
 *
 * @author lafa
 *
 */
@ThreadSafe
public class WorkerExecutorService {

    /** The name of this instance. */
    private final String name;
    /** The thread factory instance. */
    private final ExecutorThreadFactory threadFactory;
    /** The executor instance used to run the worker threads. */
    private final ExecutorService executorService;
    /** The queue of workers. */
    private final WorkerQueue workerQueue;
    /** The maximum number of concurrent threads in the executor pool. */
    private final int numThreads;
    /** True if the executor service has been shut down. */
    private volatile boolean isShutdown = false;
    /** Long timeout value for waiting for shutdown. */
    private static final int LONG_SHUTDOWN_WAIT = 10;
    /** Short timeout value for waiting for shutdown. */
    private static final int SHORT_SHUTDOWN_WAIT = 5;

    /**
     * Creates a WorkerExecutorService that creates new threads as needed, but will reuse previously constructed threads when available.
     *
     * @param numThreads the number of threads for the executor pool
     */
    public WorkerExecutorService(final int numThreads) {
        this("WorkerExecutorService thread", numThreads);
    }

    /**
     * Creates a WorkerExecutorService that creates new threads as needed, but will reuse previously constructed threads when available.
     *
     * @param clazz class whose name will be used to identify
     * @param numThreads the number of threads for the executor pool
     */
    public WorkerExecutorService(final Class<?> clazz, final int numThreads) {
        this(clazz.getName(), numThreads);
    }

    /**
     * Creates a WorkerExecutorService that creates new threads as needed, but will reuse previously constructed threads when available.
     *
     * @param name the name of this executor service
     * @param numThreads the maximum number of concurrent threads for this executor
     */
    public WorkerExecutorService(final String name, final int numThreads) {
        this.name = name;
        threadFactory = new ExecutorThreadFactory("WorkerExecutorService thread for " + name);
        this.numThreads = numThreads;
        executorService = Executors.newFixedThreadPool(numThreads, threadFactory);
        workerQueue = new WorkerQueue();
    }

    /**
     * Submits a value-returning worker for execution and returns a WorkerFuture representing the pending results of the task. The Future's get method
     * will return the task's result upon successful completion. If you would like to immediately block waiting for a task, you can use constructions
     * of the form <code> result = exec.submit(aWorker).get(); </code>
     *
     * Throws:
     *
     * RejectedExecutionException - if the worker cannot be scheduled for execution
     *
     * NullPointerException - if the worker is null
     *
     * @param <V> data type for the worker
     * @param worker the worker to be executed by this service
     * @param handler TODO
     * @return a future for the given worker
     * @throws WorkerException if an error occurs executing the worker
     */
    public <V> WorkerFuture<V> submit(final Worker<V> worker, final WorkerExceptionHandler handler) throws WorkerException {
        final WorkerWrapper<V> workerWrapper = new WorkerWrapper<V>(worker);
        // cleanup the future if number of threads is 1, as the last future is not cleaned up by the worker manager thread. WorkerManagerOneThread
        // tries to cleanup all the futures, but it cannot clean itself, causing one future to be left behind. Future size will always be 1 once the
        // last worker manager exits. So, cleanup all the future else the worker will never get executed.
        if (worker.getBlockManager() == null) {
            throw new WorkerException("getBlockManager() returned null.");
        }
        if (numThreads == 1) {
            workerQueue.removeFuturesDone();
        }
        if (workerQueue.futureSize() < numThreads) {
            // This will allow the executor to use more threads if needed
            final Future<WorkerManagerState> future = executorService.submit(new WorkerManagerOneThread(this, workerQueue));
            workerQueue.addFuture(future);
        }
        workerQueue.add(workerWrapper);
        return workerWrapper.getFuture();
    }

    /**
     * Initiates an shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.Invocation has no additional effect
     * if already shut down.
     *
     * @throws SecurityException - if a security manager exists and shutting down this ExecutorService may manipulate threads that the caller is not
     *             permitted to modify because it does not hold java.lang.RuntimePermission("modifyThread"), or the security manager's checkAccess
     *             method denies access.
     */
    public void shutdown() {
        this.shutdown(LONG_SHUTDOWN_WAIT, TimeUnit.SECONDS);
    }

    /**
     * Initiates an shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.Invocation has no additional effect
     * if already shut down. Will use the specified timeout value and unit when invoking awaitTermination on the executor service.
     *
     * @param timeout the timeout value
     * @param unit the TimeUnit for the timeout value
     *
     * @throws SecurityException - if a security manager exists and shutting down this ExecutorService may manipulate threads that the caller is not
     *             permitted to modify because it does not hold java.lang.RuntimePermission("modifyThread"), or the security manager's checkAccess
     *             method denies access.
     */
    public void shutdown(final long timeout, final TimeUnit unit) {
        if (isShutdown) {
            return;
        }
        isShutdown = true;
        executorService.shutdown();
        try {
            executorService.awaitTermination(timeout, unit);
        } catch (final InterruptedException e) {
            // Ignore
        }
        if (!executorService.isShutdown() || !executorService.isTerminated()) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(SHORT_SHUTDOWN_WAIT, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                // Ignore
            }
        }
        if (!executorService.isShutdown() || !executorService.isTerminated()) {
            throw new RuntimeException("WorkerExecutorService failed to shutdown.");
        }
    }

    /**
     * Returns true if this executor has been shut down.
     *
     * @return if this executor has been shut down
     */
    public boolean isShutdown() {
        return isShutdown;
    }
}
