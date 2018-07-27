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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of a BlockManager that only allows maxValue instances of workers to run for each blockId.
 *
 * The example code below only allow 3 simultaneous workers with the same blockId to be executed in parallel, but allows unlimited number workers with
 * different blockId workers to run in parallel.
 *
 * -- ex: Worker using a BlockManagerMaxCount for a blocking call --
 *
 * <code>
 * class SampleWorker implements Worker&lt;Integer&gt; {
 *
 *         private SampleWorkerState state;
 *
 *         private final WorkerBlockManager blockManager;
 *
 *         public ImapWorker(Long blockId, BlockManagerMaxCount.State bmState) {
 *             this.blockManager = new BlockManagerMaxCount(blockId, 3L, bmState);
 *         }
 *
 *         &#64;Override
 *         public boolean execute() throws WorkerException {
 *             if (blockManager.isAllowed()) {
 *                 return false;
 *             }
 *             switch (state) {
 *             case PICK_COMMAND:
 *                 if (blockManager.isAllowed()) {
 *                     method1ThatBlocks()  *  blocking call
 *                     state = SampleWorkerState.EXECUTE_COMMAND;
 *                 }
 *                 return false;
 *             case EXECUTE_COMMAND:
 *                 if (blockManager.isAllowed()) {
 *                     method2ThatBlocks()  *  blocking call
 *                     state = SampleWorkerState.PICK_COMMAND;
 *                 }
 *                 return true;
 *             default:
 *                 return false;
 *             }
 *         }
 *
 *         &#64;Override
 *         public WorkerBlockManager getBlockManager() {
 *             return this.blockManager;
 *         }
 *
 *         private enum ImapWorkerState {
 *             PICK_COMMAND,
 *             EXECUTE_COMMAND
 *         }
 *         ...
 * }
 * </code> -- ex: Worker using a BlockManagerMaxCount for a non blocking call -- <code>
 * class SampleWorker implements Worker&lt;Integer&gt; {
 *
 *         private SampleWorkerState state;
 *
 *         private final WorkerBlockManager blockManager;
 *
 *         public ImapWorker(Long blockId, BlockManagerMaxCount.State bmState) {
 *             this.blockManager = new BlockManagerMaxCount(blockId, 3L, bmState);
 *         }
 *
 *         &#64;Override
 *         public boolean execute() throws WorkerException {
 *             blockManager.setPersistBlockOneTimeAfterExit();
 *             if (blockManager.isAllowed()) {
 *                 return false;
 *             }
 *             switch (state) {
 *             case PICK_COMMAND:
 *                 if (blockManager.isAllowed()) {
 *                     nonBlockingMethod1()  * returns quickly
 *                     state = SampleWorkerState.EXECUTE_COMMAND;
 *                 }
 *                 return false;
 *             case EXECUTE_COMMAND:
 *                 if (blockManager.isAllowed()) {
 *                     nonBlockingMethod2()  * returns quickly
 *                     state = SampleWorkerState.PICK_COMMAND;
 *                 }
 *                 return true;
 *             default:
 *                 return false;
 *             }
 *         }
 *
 *         &#64;Override
 *         public WorkerBlockManager getBlockManager() {
 *             return this.blockManager;
 *         }
 *
 *         private enum ImapWorkerState {
 *             PICK_COMMAND,
 *             EXECUTE_COMMAND
 *         }
 *         ...
 * }
 * </code>
 *
 */
@NotThreadSafe
public class BlockManagerMaxCount implements WorkerBlockManager {

    /**
     * Shared WorkerBlockManager state which maintains a concurrent map of worker count and blockId.
     *
     */
    @ThreadSafe
    public static class SharedState {
        /** Initial map size. */
        private static final int INITIAL_MAP_SIZE = 100;
        /** worker blockId and BlockIdState map. */
        private Map<String, BlockIdState> current = new ConcurrentHashMap<String, BlockIdState>(INITIAL_MAP_SIZE);

        /**
         * Returns the worker count for given blockId.
         * @param blockId id to identify blocking worker group.
         *
         * @return current count for given blockId.
         */
        @Nonnull
        public Long getCurrent(@Nonnull final String blockId) {
            return current.get(blockId).count().get();
        }

        /**
         * Increments the worker count for given blockId.
         * @param blockId id to identify blocking worker group.
         *
         * @return worker count after increment.
         */
        @Nonnull
        public Long increment(@Nonnull final String blockId) {
            return current.get(blockId).count().incrementAndGet();
        }

        /**
         * Decrements the worker count for given blockId.
         * @param blockId id to identify blocking worker group.
         *
         * @return worker count after decrement.
         */
        @Nonnull
        public Long decrement(@Nonnull final String blockId) {
            return current.get(blockId).count().decrementAndGet();
        }

        /**
         * @param blockId id to identify blocking worker group.
         */
        public void workerCreated(@Nonnull final String blockId) {
            if (!current.containsKey(blockId)) {
                current.putIfAbsent(blockId, new BlockIdState());
            }
            current.get(blockId).inQueue().incrementAndGet();
        }

        /**
         * @param blockId id to identify blocking worker group.
         */
        public void workerDone(@Nonnull final String blockId) {
            current.get(blockId).inQueue().decrementAndGet();
        }

        /**
         * Returns the worker InQueue for given blockId.
         *
         * @param blockId id to identify blocking worker group.
         *
         * @return current InQueue for given blockId.
         */
        @Nonnull
        public Long getInQueue(@Nonnull final String blockId) {
            return current.get(blockId).inQueue().get();
        }

        /**
         * Increments the worker Unblocked for given blockId.
         *
         * @param blockId id to identify blocking worker group.
         *
         * @return workers Unblocked after increment.
         */
        @Nonnull
        public Long incrUnblocked(@Nonnull final String blockId) {
            return current.get(blockId).unblocked().incrementAndGet();
        }

        /**
         * Decrements the workers Unblocked for given blockId.
         *
         * @param blockId id to identify blocking worker group.
         *
         * @return workers Unblocked after decrement.
         */
        @Nonnull
        public Long decrUnblocked(@Nonnull final String blockId) {
            return current.get(blockId).unblocked().decrementAndGet();
        }

        /**
         * returns the workers Unblocked for given blockId.
         *
         * @param blockId id to identify blocking worker group.
         *
         * @return workers Unblocked after decrement.
         */
        public long getUnblocked(final String blockId) {
            return current.get(blockId).unblocked().get();
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            for (Map.Entry<String, BlockIdState> entry : current.entrySet()) {
                sb.append("[");
                sb.append(entry.getKey());
                sb.append("=");
                sb.append(entry.getValue());
                sb.append("]");
            }
            return sb.toString();
        }

        /** unique id. */
        private AtomicLong uniqueIdGen = new AtomicLong(0);

        /**
         * @return unique id.
         */
        public Long uniqueId() {
            return uniqueIdGen.incrementAndGet();
        }

        /**
         * Method which copies the content of the map to a WorkerStats object.
         *
         * @return Hash map of block Id as key and workerStats for that Id as value.
         */
        public Map<String, BlockIdStats> getBlockIdStatsMap() {
            /** WorkerMap is a hashmap that contains the mapping of blockId to WorkerStats for the same. */
            HashMap<String, BlockIdStats> workerMap = new HashMap<String, BlockIdStats>();
            for (Map.Entry<String, BlockIdState> entry : current.entrySet()) {
                /** new workerStats object which will be populated with blockIdState values. */
                BlockIdState blockIdState = entry.getValue();
                BlockIdStats blockIdStats = new BlockIdStats(blockIdState.count().longValue(), blockIdState.unblocked().longValue(),
                        blockIdState.inQueue().longValue());
                workerMap.put(entry.getKey(), blockIdStats);
            }
            return workerMap;

        }
    };

    /**
     * State of BlockId.
     *
     */
    @ThreadSafe
    private static class BlockIdState {
        /**
         * Worker count for given blockId.
         */
        private AtomicLong count = new AtomicLong(0);

        /**
         * @return count.
         */
        public AtomicLong count() {
            return count;
        }

        /**
         * Workers unblocked for given blockId.
         */
        private AtomicLong unblocked = new AtomicLong(0);

        /**
         * @return unblocked.
         */
        public AtomicLong unblocked() {
            return unblocked;
        }

        /**
         * Workers inQueue for given blockId.
         */
        private AtomicLong inQueue = new AtomicLong(0);

        /**
         * @return unblocked.
         */
        public AtomicLong inQueue() {
            return inQueue;
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[count:").append(count.get());
            sb.append(", inQueue:").append(inQueue.get());
            sb.append(", unblocked:").append(unblocked.get()).append("]");
            return sb.toString();
        }
    }

    /**
     * State of BlockId.
     *
     */
    public static class State {
        /**
         * Constructor.
         * @param blockId the id to block against
         * @param maxValue the max value for the state.
         * @param sharedState the state that is shared across multiple executions.
         */
        public State(@Nonnull final String blockId, @Nonnull final Long maxValue, @Nonnull final SharedState sharedState) {
            this.blockId = blockId;
            this.maxValue = maxValue;
            this.sharedState = sharedState;
            this.stateId = sharedState.uniqueId();
        }

        /** Reference to WorkerBlockManager SharedState. */
        @Nonnull
        private final SharedState sharedState;

        /** BlockId. */
        @Nonnull
        private final String blockId;

        /** Maximum value of worker counts allowed. */
        @Nonnull
        private final Long maxValue;

        /** Unique Id of this manager. */
        @Nonnull
        private final Long stateId;

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("[stateId:").append(stateId).append(", ");
            sb.append("blockId:").append(blockId).append(", ");
            sb.append("maxValue:").append(maxValue).append(", ");
            sb.append("shared:").append(sharedState).append("]");
            return sb.toString();
        }

    }

    /**Reference to WorkerBlockManager State.*/
    private final Set<State> stateRef;

    /** if the lock should be release after the excute method returns for one cycle. */
    private boolean persistblockOneTimeAfterExit = false;

    /** if this worker is blocked. */
    private Boolean allowedCache = false;

    /** Lock on the SharedState. */
    private final Object lock;

    /**
     * Instantiates new BlockManager which allows maxValue instances of workers to run for each blockId.
     *
     * @param stateRef collection of states.
     */
    public BlockManagerMaxCount(@Nonnull final Set<State> stateRef) {
        for (State state : stateRef) {
            state.sharedState.workerCreated(state.blockId);
        }
        this.stateRef = stateRef;
        this.lock = stateRef;
    }

    @Override
    public void enterExecuteCall() {
        if (!persistblockOneTimeAfterExit) {
            // Update shared state
            synchronized (lock) {
                // Increment the concurrency
                for (State state : stateRef) {
                    state.sharedState.increment(state.blockId);
                }

                // reset Unblocked selection
                allowedCache = true;
                for (State state : stateRef) {
                    if (state.sharedState.getCurrent(state.blockId) > state.maxValue) {
                        allowedCache = false;
                    }
                }

                // Increment the current Unblocked Workers
                if (allowedCache) {
                    for (State state : stateRef) {
                        state.sharedState.incrUnblocked(state.blockId);
                    }
                }
            }
        }

        // reset persistblockOneTimeAfterExit
        persistblockOneTimeAfterExit = false;

    }

    @Override
    public void exitExecuteCall(final boolean isWorkerDone) {
        // If isWorkerDone is not done and persistblockOneTimeAfterExit is true and allowedCache is set to true
        // then keep the value and don't decrement
        if (!isWorkerDone && persistblockOneTimeAfterExit && allowedCache) {
            return;
        }

        // Update shared state
        synchronized (lock) {
            for (State state : stateRef) {
                // decrement the concurrency
                state.sharedState.decrement(state.blockId);
                // marked the worker as done
                if (isWorkerDone) {
                    state.sharedState.workerDone(state.blockId);
                }
                // decrement the current Unblocked Workers
                if (allowedCache) {
                    state.sharedState.decrUnblocked(state.blockId);
                }
            }
        }
        // reset local state
        persistblockOneTimeAfterExit = false;
        allowedCache = false;
    }

    @Override
    public boolean isAllowed() {
        return allowedCache;
    }

    @Override
    public void setPersistBlockOneTimeAfterExit() {
        persistblockOneTimeAfterExit = true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[allowed:").append(allowedCache);
        sb.append(", persistblockOneTimeAfterExit:").append(allowedCache).append(", ");

        for (State state : stateRef) {
            sb.append(state.toString());
            sb.append(", ");
        }
        return sb.toString();
    }

    @Override
    public String toStateString() {
        StringBuffer sb = new StringBuffer(2048);
        sb.append("bs:[");
        for (State state : stateRef) {
            final String blockId = state.blockId;
            final SharedState sharedState = state.sharedState;
            sb.append("[id:").append(blockId);
            sb.append(",iQ:").append(sharedState.getInQueue(blockId)).append("]");
        }
        sb.append("]");
        return sb.toString();
    }
}
