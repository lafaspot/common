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

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * This class is return by the Future of a WorkerOneThreadMAnager instance.
 *
 */
@NotThreadSafe
public class WorkerManagerState {
    /**
     * Instantiates new WorkerManagerState.
     *
     * @param mWorkers list of WorkerWrapper.
     */
    public WorkerManagerState(@Nonnull final List<WorkerWrapper> mWorkers) {
        // lafa - implement this
    }

}
