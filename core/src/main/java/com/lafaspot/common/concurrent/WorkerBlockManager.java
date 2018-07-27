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

/**
 * This interface is used to restrict the number of worker that can be executed at the same time.
 *
 */
@Nullable
public interface WorkerBlockManager {

    /**
     * @return if this Worker is allowed to execute. This method can't throw any exceptions.
     */
    @Nullable
    boolean isAllowed();

    /**
     * Force the manager to not release the block until the next execute cycle. On true the block is always released.
     */
    @Nullable
    void setPersistBlockOneTimeAfterExit();

    /**
     * Internally called by the WorkExecutor before execute is called. This method can't throw any exceptions.
     */
    @Nullable
    void enterExecuteCall();

    /**
     * Internally called by the WorkExecutor after execute returns. This method can't throw any exceptions.
     *
     * @param workerExitedWithTrue boolean which indicates how the worker exited.
     */
    @Nullable
    void exitExecuteCall(boolean workerExitedWithTrue);

    /**
     * Serialize the stat string for debugging purposes.
     * @return the string containing the stats, only toe be used for debugging.
     */
    @Nonnull
    String toStateString();
}
