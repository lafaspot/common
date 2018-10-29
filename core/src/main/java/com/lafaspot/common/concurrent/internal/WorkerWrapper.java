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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lafaspot.common.concurrent.Worker;
import com.lafaspot.common.concurrent.WorkerException;
import com.lafaspot.common.concurrent.WorkerFuture;

/**
 * WorkerWrapper holds a reference to the future and the cancel state of this worker, this class is used in the WorkerManagerOneThread.
 *
 * @param <V> the result returned by the worker.
 */
@NotThreadSafe
public class WorkerWrapper<V> {

    /** Worker instance for this wrapper. */
    @Nonnull
    private final Worker<V> worker;
    /** Future associated with this worker. */
    @Nonnull
    private final WorkerFutureImpl<V> future;
    /** Keeps track of whether or not the worker has been canceled. */
    @Nonnull
    private final AtomicBoolean canceled = new AtomicBoolean(false);
    /** Logger instance. */
    @Nonnull
    private final Logger logger = LoggerFactory.getLogger(WorkerWrapper.class);
    /** {@link WorkerStats} instance to record stats for this worker. */
    @Nonnull
    private final WorkerStats stats;

    /**
     * Creates an instance of the wrapper with the given worker and allocates a new Future.
     *
     * @param worker the worker instance
     */
    public WorkerWrapper(final Worker<V> worker) {
        this.worker = worker;
        future = new WorkerFutureImpl<V>(this);
        this.stats = new WorkerStats(worker.getClass());
    }

    /**
     * Calls execute on the worker instance and returns true if execution is completed, else returns false. This method should be called repeatedly
     * until the work is complete.
     *
     * @return true if the work is complete, else false
     * @throws WorkerException if an exception occurs in the worker
     */
    protected boolean execute() throws WorkerException {
        boolean done = true;
        Exception exception = null;
        V data = null;
        try {
            stats.recordBeginExecute();
            done = canceled.get() || worker.execute();
            if (done) {
                if (worker.hasErrors()) {
                    exception = worker.getCause();
                } else {
                    data = worker.getData();
                }
            }
        } catch (final Exception e) {
            logger.error("Uncaught exception occurred in worker", e);
            exception = e;
            stats.recordExceptionTriggered(e);
        }
        try {
            if (done) {
                worker.cleanup();
            }
        } catch (final Exception e) {
            logger.error("Uncaught exception occurred in worker", e);
            exception = e;
            stats.recordExceptionTriggered(e);
        }
        if (done) {
            if (exception != null) {
                future.done(canceled.get(), exception);
            } else {
                future.done(data, canceled.get());
            }
        }
        data = null;
        exception = null;
        stats.recordEndExecute(done);
        return done;
    }

    /**
     * @return the future for this worker
     */
    @Nonnull
    public WorkerFuture<V> getFuture() {
        return future;
    }

    /**
     * This method is called by the WorkerFutureImpl and needs to be thread safe. Current implementation will not support interrupting the thread
     *
     * @param mayInterruptIfRunning true if interruption is allowed, else false
     */
    protected void cancel(final boolean mayInterruptIfRunning) {
        canceled.set(true);
        if (canceled.get()) {
            stats.recordCanceled();
        }
    }

    /**
     * @return the worker instance
     */
    @Nonnull
    protected Worker<V> getWorkerImpl() {
        return worker;
    }

    /**
     * @return the worker stats
     */
    @Nonnull
    public WorkerStats getStat() {
        return stats;
    }

}