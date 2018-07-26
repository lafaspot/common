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

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Implementation of a BlockManager that does not do anything.
 *
 * @author lafa
 *
 */
@NotThreadSafe
public final class BlockManagerUnlimited implements WorkerBlockManager {

    /**
     * Unlimited capacity for workers.
     */
    public static final BlockManagerUnlimited UNLIMITED = new BlockManagerUnlimited();

    /**
     * Private constructor.
     */
    private BlockManagerUnlimited() {
    }

    @Override
    public void enterExecuteCall() {
        // NOOP
    }

    @Override
    public void exitExecuteCall(final boolean isWorkerDone) {
        // NOOP
    }

    @Override
    public boolean isAllowed() {
        return true;
    }

    @Override
    public void setPersistBlockOneTimeAfterExit() {
    }

    @Override
    public String toStateString() {
        return "";
    }

}
