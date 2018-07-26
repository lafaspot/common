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

import javax.annotation.Nonnull;

import com.lafaspot.common.concurrent.Worker;

/**
 * Worker stats to record worker execute and completion time.
 *
 * @author lafa
 *
 */
public class WorkerStats {

    /** Specifies the Worker {@link Class} clazz. */
    @Nonnull
    private final Class<? extends Worker> clazz;

    /** Records the creation time for the worker specified by {@link Class} clazz. */
    private final long createTime;

    /** Records the last execute time for the worker specified by {@link Class} clazz. */
    private long lastExecuteTime;

    /** Records the end time for the worker specified by {@link Class} clazz. */
    private long endTime;

    /** Records the total duration of the worker specified by {@link Class} clazz. */
    private long totalDuration;

    /** Records the total count of exception thrown by the worker specified by {@link Class} clazz.*/
    private long exceptionCount;

    /** Records the total count of cancel operation called on the worker specified by {@link Class} clazz.*/
    private long cancelCount;

    /** Records the timestamp when the last execute call completed. */
    private long endExecuteTime;

    /**
     * Constructs an instance of {@link WorkerStats} for worker specified by {@link Class} clazz.
     *
     * @param clazz the Class<? extends Worker> instance
     */
    public WorkerStats(@Nonnull final Class<? extends Worker> clazz) {
        this.createTime = System.nanoTime();
        this.clazz = clazz;
    }

    /**
     * Records the begin execute time for the worker in nano seconds.
     */
    public void recordBeginExecute() {
        lastExecuteTime = System.nanoTime();
    }

    /**
     * Records the end execute time for the worker in nano seconds.
     *
     * @param done boolean specifying if the worker is completed
     */
    public void recordEndExecute(final boolean done) {
        endExecuteTime = System.nanoTime();
        if (done) {
            endTime = endExecuteTime;
        }
        totalDuration += (endExecuteTime - lastExecuteTime);
    }

    /**
     * Records the count of total number of exceptions triggered during the worker life cycle.
     *
     * @param exception the exception triggered by the worker specified by {@link Class} clazz
     */
    public void recordExceptionTriggered(@Nonnull final Exception exception) {
        exceptionCount = exceptionCount + 1;
    }

    /**
     * Records the count of total number of cancel operation called on the worker during the worker life cycle.
     */
    public void recordCanceled() {
        cancelCount = cancelCount + 1;
    }

    /**
     * @return the worker stats as string for the worker specified by {@link Class} clazz
     */
    @Nonnull
    public String getStatsAsString() {
        final StringBuffer sb = new StringBuffer();
        sb.append("WorkerClass=").append(clazz.getName()).append(" ");
        sb.append("WorkerCreateTime=").append(createTime).append(" ");
        sb.append("WorkerEndTime=").append(endTime).append(" ");
        sb.append("WorkerLastExecuteDuration=").append(endExecuteTime - lastExecuteTime).append(" ");
        sb.append("WorkerDuration=").append(totalDuration).append(" ");
        sb.append("WorkerExceptionCount=").append(exceptionCount).append(" ");
        sb.append("WorkerCancelCount=").append(cancelCount).append(".");
        return sb.toString();
    }

}