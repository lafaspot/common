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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.concurrent.ThreadSafe;

import com.lafaspot.common.concurrent.WorkerFuture;

/**
 * @see WorkerFuture
 *
 * @param <T> the data returned by the worker
 */
@ThreadSafe
public class WorkerFutureImpl<T> implements WorkerFuture<T> {

    // Using Atomic to keep the read method lock free
    /** Indicates whether the worker is done or not. */
    private final AtomicBoolean isDone = new AtomicBoolean(false);
    /** Indicates whether the workere has been canceled or not. */
    private final AtomicBoolean isCanceled = new AtomicBoolean(false);
    /** Reference to the data result of the worker. */
    private final AtomicReference<T> dataRef = new AtomicReference<T>();
    /** Reference to the worker instance. */
    private final AtomicReference<WorkerWrapper<T>> workerRef = new AtomicReference<WorkerWrapper<T>>();
    /** Lock used for this class. */
    private final Object lock = new Object();
    /** Reference to the exception cause if the worker encountered an error. */
    private final AtomicReference<Exception> causeRef = new AtomicReference<Exception>();
    /** Wait interval when the user calls get(). */
    private static final int GET_WAIT_INTERVAL_MILLIS = 1000;

    /**
     * Constructs a WorkerFutureImpl with a Future. Constructor is called by the WorkerExecutorService.
     *
     * @param worker the worker instance
     */
    public WorkerFutureImpl(final WorkerWrapper<T> worker) {
        workerRef.set(worker);
    }

    /*
     * Attempts to cancel execution of this task. This attempt will fail if the task has already completed, has already been cancelled, or could not
     * be cancelled for some other reason. If successful, and this task has not started when cancel is called, this task should never run. If the task
     * has already started, then the mayInterruptIfRunning parameter determines whether the thread executing this task should be interrupted in an
     * attempt to stop the task. After this method returns, subsequent calls to isDone() will always return true. Subsequent calls to isCancelled()
     * will always return true if this method returned true.
     *
     * @see java.util.concurrent.Future#cancel(boolean)
     */
    @Override
    public boolean cancel(final boolean mayInterruptIfRunning) {
        synchronized (lock) {
            if (workerRef.get() != null) {
                workerRef.get().cancel(mayInterruptIfRunning);
            }
            done(true, null);
        }
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.Future#isCancelled()
     */
    @Override
    public boolean isCancelled() {
        return isCanceled.get();
    }

    /**
     * Invoked when the worker has completed its processing.
     *
     * @param data data to be set
     * @param canceled true if the call was the result of a cancellation
     */
    protected void done(final T data, final boolean canceled) {
        // Assign internal state of the future, make sure we copy local references and lose reference to the worker
        synchronized (lock) {
            if (!isDone.get()) {
                // Unset Worker reference when worker is done
                WorkerWrapper<T> worker = workerRef.get();
                if (workerRef.compareAndSet(worker, null)) {
                    dataRef.set(data);
                    isDone.set(true);
                    isCanceled.set(canceled);
                }
            }
            lock.notify();
        }
    }

    /**
     * Invoked when the worker throws an exception.
     *
     * @param canceled is this future cancelled
     * @param cause the exception that caused execution to fail
     */
    protected void done(final boolean canceled, final Exception cause) {
        // Assign internal state of the future, make sure we copy local references and lose reference to the worker
        synchronized (lock) {
            if (!isDone.get()) {
                // Unset Worker reference when worker is done
                WorkerWrapper<T> worker = workerRef.get();
                if (workerRef.compareAndSet(worker, null)) {
                    causeRef.set(cause);
                    isDone.set(true);
                    isCanceled.set(canceled);
                }
            }
            lock.notify();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.Future#isDone()
     */
    @Override
    public boolean isDone() {
        return isDone.get();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.Future#get()
     */
    @Override
    public T get() throws InterruptedException, ExecutionException, CancellationException {
        synchronized (lock) {
            while (!isDone.get()) {
                lock.wait(GET_WAIT_INTERVAL_MILLIS);
            }
            lock.notify();
        }
        if (causeRef.get() != null) {
            throw new ExecutionException(causeRef.get());
        } else if (isCancelled()) {
            throw new CancellationException();
        } else {
            return dataRef.get();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException, CancellationException {
        synchronized (lock) {
            if (!isDone.get()) {
                lock.wait(unit.toMillis(timeout));
            }
            lock.notify();
        }
        if (isDone.get()) {
            if (isCancelled()) {
                throw new CancellationException();
            } else if (causeRef.get() != null) {
                throw new ExecutionException(causeRef.get());
            } else {
                return dataRef.get();
            }
        } else {
            throw new TimeoutException("Timeout reached.");
        }
    }

}
