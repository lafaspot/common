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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Worker interface.
 *
 * @param <T> the return data type.
 */
@NotThreadSafe
public interface Worker<T> {
    /**
     * This method will be called multiple times. Make sure to do your work and return as soon as possible.
     *
     * @return true when Worker is done return false otherwise
     * @throws WorkerException if error occurs.
     */
    boolean execute() throws WorkerException;

    /**
     * Should only be called after execute method returns true. In case there is no errors this method will return null.
     *
     * @return cause of the exception.
     */
    @Nullable
    Exception getCause();

    /**
     * Should only be called after execute method returns true. Returns true is the worker failed during the execution.
     *
     * @return boolean to indicate whether worker execution has error.
     */
    boolean hasErrors();

    /**
     * getData will return null in case of error or when no data is produced. Should only be called after execute method returns true.
     *
     * @return the data
     */
    @Nullable
    T getData();

    /**
     * getBlockManager will return the WorkerBlockManager implementation for this worker. Will be called on submit action.
     *
     * @return the WorkerBlockManager
     */
    @Nonnull
    WorkerBlockManager getBlockManager();

    /**
     * Cleanup the worker when it is done.
     */
    void cleanup();

}
