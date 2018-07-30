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

import java.util.concurrent.ThreadFactory;

/**
 * Create daemon threads.
 *
 */
public class ExecutorThreadFactory implements ThreadFactory {

    private ThreadGroup mGroup = null;
    private volatile long mCount = 1;

    /**
     * takes a thread group name as an argument.
     *
     * @param groupName the name of the group.
     */
    public ExecutorThreadFactory(final String groupName) {
        mGroup = new ThreadGroup(groupName);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
     */
    @Override
    public Thread newThread(final Runnable target) {
        // Threads with 4k stack
        final Thread t = new Thread(mGroup, target, mGroup.getName() + "-" + ++mCount);
        // jvm should not wait for these thread before it exits
        t.setDaemon(true);
        return t;
    }
}
