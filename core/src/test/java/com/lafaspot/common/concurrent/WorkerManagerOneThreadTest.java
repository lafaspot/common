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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nonnull;

import org.testng.Assert;
import org.testng.annotations.Test;


/**
 * Unit test for {@link WorkerManagerOneThread}.
 *
 */
public class WorkerManagerOneThreadTest {

    /**
     * Tests multiple workers in one worker thread.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @Test
    public void testWorkerManagerOneThreadWithMultipleWorkers() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(1, new WorkerConfig.Builder().build());
        final List<ImapWorker> workers = new ArrayList<>();
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        int workerCount = 100;
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final String blockId = "1560";
        for (int i = 0; i < workerCount; i++) {
            final ImapWorker imapWorker = new ImapWorker(i, blockId, state);
            workers.add(imapWorker);
            futures.add(exec.submit(imapWorker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            final int executeCount = futures.get(i).get(60, TimeUnit.SECONDS);
            Assert.assertEquals(executeCount, i, "Execute count mismatch.");
        }

        exec.shutdown();

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 1 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 1 after all worker is done");

        long totalLatency = 0;
        int totalCommand = 0;
        for (int i = 0; i < workerCount; i++) {
            ImapWorker worker = workers.get(i);
            totalLatency += worker.totalLatency;
            totalCommand += worker.commandCount;

        }
        double latency = totalLatency / totalCommand;
        Assert.assertTrue(latency < workerCount / 2, "Latency should less than worker count divide by 2");
    }

    /**
     * Tests one worker thread with workers that throw exceptions.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testWorkerManagerWithExecutionExceptions() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(1, new WorkerConfig.Builder().build());
        final List<Worker> workers = new ArrayList<>();
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        int workerCount = 100;
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final String blockId = "1560";
        for (int i = 0; i < workerCount; i++) {
            Worker worker = null;
            if (i % 2 == 0) {
                worker = new ImapWorker(i, blockId, state);
            } else {
                worker = new WorkerWithException(i, blockId, state);
            }
            workers.add(worker);
            futures.add(exec.submit(worker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            if (i % 2 == 0) {
                final int executeCount = futures.get(i).get(60, TimeUnit.SECONDS);
                Assert.assertEquals(executeCount, i, "Execute count mismatch.");
            } else {
                try {
                    futures.get(i).get(60, TimeUnit.SECONDS);
                    Assert.fail("Should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertTrue(ex.getCause() instanceof WorkerException, "Exception cause does not match");
                }
            }
        }

        exec.shutdown();

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 1 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 1 after all worker is done");

    }

    /**
     * Tests one worker thread with workers that throw unchecked exceptions.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testWorkerManagerWithUncheckedExceptions() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(1, new WorkerConfig.Builder().build());
        final List<Worker> workers = new ArrayList<>();
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        int workerCount = 100;
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final String blockId = "1560";
        for (int i = 0; i < workerCount; i++) {
            Worker worker = null;
            if (i % 2 == 0) {
                worker = new ImapWorker(i, blockId, state);
            } else {
                worker = new WorkerWithUncheckedException(i, blockId, state);
            }
            workers.add(worker);
            futures.add(exec.submit(worker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            if (i % 2 == 0) {
                final int executeCount = futures.get(i).get(60, TimeUnit.SECONDS);
                Assert.assertEquals(executeCount, i, "Execute count mismatch.");
            } else {
                try {
                    futures.get(i).get(60, TimeUnit.SECONDS);
                    Assert.fail("Should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertTrue(ex.getCause() instanceof RuntimeException, "Exception cause does not match");
                }
            }
        }

        exec.shutdown();

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 1 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 1 after all worker is done");

    }

    /**
     * Imap worker state.
     *
     * @author kaituo
     *
     */
    enum ImapWorkerState {
        /** Pick command. */
        PICK_COMMAND,
        /** Execute command. */
        EXECUTE_COMMAND
    }

    /**
     * Fake Imap worker used to test mutilple workers in one thread.
     *
     * @author kaituo
     *
     */
    public class ImapWorker implements Worker<Integer> {

        /** Worker Id. */
        private int workerId;
        /** Command start time. */
        private long commandStartTime;
        /** Worker execute count. */
        private int executeCount;
        /** Command total latency. */
        private long totalLatency;
        /** COmmand total count. */
        private int commandCount;
        /** Imap worker state. */
        private ImapWorkerState state;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;

        /**
         * Constructor of ImapWorker.
         *
         * @param workerId worker Id
         * @param blockId block Id
         * @param state BlockManager state
         */
        public ImapWorker(final int workerId, @Nonnull final String blockId, @Nonnull final BlockManagerMaxCount.SharedState state) {
            this.workerId = workerId;
            this.state = ImapWorkerState.PICK_COMMAND;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, 5L, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
        }

        @Override
        public boolean execute() throws WorkerException {
            if (executeCount == workerId) {
                return true;
            }
            executeCount++;
            switch (state) {
            case PICK_COMMAND:
                if (blockManager.isAllowed() && workerId % 7 == 0) {
                    state = ImapWorkerState.EXECUTE_COMMAND;
                    commandStartTime = System.currentTimeMillis();
                    commandCount++;
                }
                return false;
            case EXECUTE_COMMAND:
                if (blockManager.isAllowed()) {
                    long latency = System.currentTimeMillis() - commandStartTime;
                    totalLatency += latency;
                    state = ImapWorkerState.PICK_COMMAND;
                }
                return false;
            default:
                return false;
            }
        }

        @Override
        public Exception getCause() {
            return null;
        }

        @Override
        public boolean hasErrors() {
            return false;
        }

        @Override
        public Integer getData() {
            return executeCount;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return this.blockManager;
        }

        @Override
        public void cleanup() {
            state = null;
        }
    }

    /**
     * Test worker to throw checked exception in execute().
     *
     */
    public class WorkerWithException implements Worker<Integer> {

        /** Worker execute count. */
        private int executeCount;
        /** Imap worker state. */
        private Exception exception;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;

        /**
         * Constructor of WorkerWithException.
         *
         * @param workerId worker Id
         * @param blockId block Id
         * @param state BlockManager state
         */
        public WorkerWithException(final int workerId, @Nonnull final String blockId, @Nonnull final BlockManagerMaxCount.SharedState state) {
            this.executeCount = 0;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, 5L, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
        }

        @Override
        public boolean execute() throws WorkerException {
            executeCount++;
            exception = new Exception("dummy exception");
            throw new WorkerException(exception);
        }

        @Override
        public Exception getCause() {
            return exception;
        }

        @Override
        public boolean hasErrors() {
            return true;
        }

        @Override
        public Integer getData() {
            return executeCount;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return this.blockManager;
        }

        @Override
        public void cleanup() {
            exception = null;
        }
    }

    /**
     * Test worker to throw unchecked exception in execute().
     *
     */
    public class WorkerWithUncheckedException implements Worker<Integer> {

        /** Worker execute count. */
        private int executeCount;
        /** exception to be thrown. */
        private RuntimeException exception;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;

        /**
         * Constructor of WorkerWithUncheckedException.
         *
         * @param workerId worker Id
         * @param blockId block Id
         * @param state BlockManager state
         */
        public WorkerWithUncheckedException(final int workerId, @Nonnull final String blockId,
                @Nonnull final BlockManagerMaxCount.SharedState state) {
            this.executeCount = 0;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, 5L, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
        }

        @Override
        public boolean execute() throws WorkerException {
            executeCount++;
            exception = new RuntimeException("dummy exception");
            throw exception;
        }

        @Override
        public Exception getCause() {
            return exception;
        }

        @Override
        public boolean hasErrors() {
            return true;
        }

        @Override
        public Integer getData() {
            return executeCount;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return this.blockManager;
        }

        @Override
        public void cleanup() {
            exception = null;
        }
    }

    /**
     * Sample worker exception handler.
     *
     */
    class WorkerExceptionHandlerImpl implements WorkerExceptionHandler<Throwable> {

        @Override
        public void failed(final Throwable exception) {
            // no-op.
        }
    }
}
