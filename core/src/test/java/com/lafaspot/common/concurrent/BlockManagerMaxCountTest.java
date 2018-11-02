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

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for {@link BlockManagerMaxCount}.
 *
 */
public final class BlockManagerMaxCountTest {

    /**
     * Tests multiple BlockManagerMaxCount in 6 worker threads, to validate that 2 different blockId get blocked at the same rate.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @Test
    public void testMultipleWorkers() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(10);
        final List<ImapWorker> workers = new ArrayList<>();
        final List<WorkerFuture<Long>> futures = new ArrayList<WorkerFuture<Long>>();
        final int workerCount = 100;
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);
        final AtomicInteger max1 = new AtomicInteger(0);
        final AtomicInteger max2 = new AtomicInteger(0);
        final String blockId1 = "1560";
        final String blockId2 = "1570";
        for (int i = 0; i < workerCount; i++) {
            final ImapWorker imapWorker;
            if (i % 2 == 0) {
                imapWorker = new ImapWorker(blockId1, state, max1, counter1);
                Assert.assertNotNull(imapWorker.getBlockManager().toString());
            } else {
                imapWorker = new ImapWorker(blockId2, state, max2, counter2);
                Assert.assertNotNull(imapWorker.getBlockManager().toString());
            }
            workers.add(imapWorker);
            futures.add(exec.submit(imapWorker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            final long latency = futures.get(i).get(60, TimeUnit.SECONDS);
            Assert.assertTrue(latency >= 100, "Latency mismatch.");
        }

        exec.shutdown();

        // some worker will start and exit right away.
        Assert.assertTrue(max1.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max1.get());
        Assert.assertTrue(max2.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max2.get());

        long totalLatency = 0;
        int totalExecuteCount = 0;
        for (int i = 0; i < workerCount; i++) {
            ImapWorker worker = workers.get(i);
            totalLatency += worker.totalLatency;
            totalExecuteCount += worker.executeCount;

        }
        double latency = totalLatency / 100;
        Assert.assertTrue(latency > 100, "Latency should be greater than 100: " + latency);

        Assert.assertEquals(state.getCurrent(blockId1).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId1), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId1).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

        Assert.assertEquals(state.getCurrent(blockId2).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId2), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId2).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

    }

    /**
     * Test multiple workers which throws checked exceptions. Verify that {@code BlockManagerMaxCount} is able to increment and decrement worker
     * counters properly.
     *
     * @throws WorkerException never unless test is defective.
     * @throws InterruptedException never unless test is defective.
     * @throws TimeoutException never unless test is defective.
     * @throws ExecutionException never unless test is defective.
     */
    @Test
    public void testMultipleWorkersWithCheckedException() throws WorkerException, InterruptedException, TimeoutException, ExecutionException {
        final WorkerExecutorService workerExecutor = new WorkerExecutorService(10);
        final int totalWorkers = 1;
        final String blockId = "blockId";
        final AtomicInteger max = new AtomicInteger(0);
        final AtomicInteger sharedCounter = new AtomicInteger(0);
        final BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final List<WorkerFuture<Long>> futures = new ArrayList<>(totalWorkers);
        for (int i = 0; i < totalWorkers; i++) {
            final Worker<Long> worker;
            if (i % 2 == 0) {
                worker = new WorkerWithCheckedException(blockId, state, max, sharedCounter);
            } else {
                worker = new ImapWorker(blockId, state, max, sharedCounter);
            }
            futures.add(workerExecutor.submit(worker, new WorkerExceptionHandlerImpl()));
        }

        for (int i = 0; i < totalWorkers; i++) {
            final WorkerFuture<Long> workerFuture = futures.get(i);
            if (i % 2 == 0) {
                try {
                    workerFuture.get(60, TimeUnit.SECONDS);
                    Assert.fail("should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertNotNull(ex.getCause(), "cause of ExecutionException should not be null");
                    Assert.assertTrue(ex.getCause() instanceof WorkerException);
                }
            } else {
                final long latency = workerFuture.get(60, TimeUnit.SECONDS); // ImapWorker is not expected to throw exception
                Assert.assertTrue(latency >= 100, "Latency mismatch.");
            }
        }
        workerExecutor.shutdown();

        Assert.assertTrue(max.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max.get());

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

    }

    /**
     * Test multiple workers which throws unchecked exceptions. Verify that {@code BlockManagerMaxCount} is able to increment and decrement worker
     * counters properly.
     *
     * @throws WorkerException never unless test is defective.
     * @throws InterruptedException never unless test is defective.
     * @throws TimeoutException never unless test is defective.
     * @throws ExecutionException never unless test is defective.
     */
    @Test
    public void testMultipleWorkersWithUncheckedException() throws WorkerException, InterruptedException, TimeoutException, ExecutionException {
        final int numThreads = 10;
        final WorkerExecutorService workerExecutor = new WorkerExecutorService(numThreads);
        final int totalWorkers = 100;
        final String blockId = "blockId";
        final AtomicInteger max = new AtomicInteger(0);
        final AtomicInteger sharedCounter = new AtomicInteger(0);
        final BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final List<WorkerFuture<Long>> futures = new ArrayList<>(totalWorkers);
        for (int i = 0; i < totalWorkers; i++) {
            final Worker<Long> worker;
            if (i % 2 == 0) {
                worker = new WorkerWithUncheckedException(blockId, state, max, sharedCounter);
            } else {
                worker = new ImapWorker(blockId, state, max, sharedCounter);
            }
            futures.add(workerExecutor.submit(worker, new WorkerExceptionHandlerImpl()));
        }

        for (int i = 0; i < totalWorkers; i++) {
            final WorkerFuture<Long> workerFuture = futures.get(i);
            if (i % 2 == 0) {
                try {
                    workerFuture.get(60, TimeUnit.SECONDS);
                    Assert.fail("should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertNotNull(ex.getCause(), "cause of ExecutionException should not be null");
                    Assert.assertTrue(ex.getCause() instanceof RuntimeException);
                }
            } else {
                final long latency = workerFuture.get(60, TimeUnit.SECONDS); // ImapWorker is not expected to throw exception
                Assert.assertTrue(latency >= 100, "Latency mismatch.");
            }
        }
        workerExecutor.shutdown();

        Assert.assertTrue(max.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max.get());

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

    }

    /**
     * Test multiple workers which throws checked exceptions. Verify that {@code BlockManagerMaxCount} is able to increment and decrement worker
     * counters properly.
     *
     * @throws WorkerException never unless test is defective.
     * @throws InterruptedException never unless test is defective.
     * @throws TimeoutException never unless test is defective.
     * @throws ExecutionException never unless test is defective.
     */
    @Test
    public void testMultipleAysncWorkersWithCheckedException() throws WorkerException, InterruptedException, TimeoutException, ExecutionException {
        final int numThreads = 10;
        final WorkerExecutorService workerExecutor = new WorkerExecutorService(numThreads);
        final int totalWorkers = 100;
        final String blockId = "blockId";
        final AtomicInteger max = new AtomicInteger(0);
        final AtomicInteger sharedCounter = new AtomicInteger(0);
        final BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final List<WorkerFuture<Long>> futures = new ArrayList<>(totalWorkers);
        for (int i = 0; i < totalWorkers; i++) {
            final Worker<Long> worker;
            if (i % 2 == 0) {
                worker = new WorkerWithCheckedExceptionAsync(i, blockId, state, max, sharedCounter);
            } else {
                worker = new ImapWorkerAsync(i, blockId, state, max, sharedCounter);
            }
            futures.add(workerExecutor.submit(worker, new WorkerExceptionHandlerImpl()));
        }

        for (int i = 0; i < totalWorkers; i++) {
            final WorkerFuture<Long> workerFuture = futures.get(i);
            if (i % 2 == 0) {
                try {
                    workerFuture.get(60, TimeUnit.SECONDS);
                    Assert.fail("should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertNotNull(ex.getCause(), "cause of ExecutionException should not be null");
                    Assert.assertTrue(ex.getCause() instanceof WorkerException);
                }
            } else {
                final long executeCount = workerFuture.get(60, TimeUnit.SECONDS);
                Assert.assertEquals(executeCount, i, "result mismatch");
            }
        }
        workerExecutor.shutdown();

        Assert.assertTrue(max.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max.get());

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

    }

    /**
     * Test multiple workers which throws un-checked exceptions. Verify that {@code BlockManagerMaxCount} is able to increment and decrement worker
     * counters properly.
     *
     * @throws WorkerException never unless test is defective.
     * @throws InterruptedException never unless test is defective.
     * @throws TimeoutException never unless test is defective.
     * @throws ExecutionException never unless test is defective.
     */
    @Test
    public void testMultipleAysncWorkersWithUncheckedException() throws WorkerException, InterruptedException, TimeoutException, ExecutionException {
        final int numThreads = 10;
        final WorkerExecutorService workerExecutor = new WorkerExecutorService(numThreads);
        final int totalWorkers = 100;
        final String blockId = "blockId";
        final AtomicInteger max = new AtomicInteger(0);
        final AtomicInteger sharedCounter = new AtomicInteger(0);
        final BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final List<WorkerFuture<Long>> futures = new ArrayList<>(totalWorkers);
        for (int i = 0; i < totalWorkers; i++) {
            final Worker<Long> worker;
            if (i % 2 == 0) {
                worker = new WorkerWithUncheckedExceptionAsync(i, blockId, state, max, sharedCounter);
            } else {
                worker = new ImapWorkerAsync(i, blockId, state, max, sharedCounter);
            }
            futures.add(workerExecutor.submit(worker, new WorkerExceptionHandlerImpl()));
        }

        for (int i = 0; i < totalWorkers; i++) {
            final WorkerFuture<Long> workerFuture = futures.get(i);
            if (i % 2 == 0) {
                try {
                    workerFuture.get(60, TimeUnit.SECONDS);
                    Assert.fail("should have thrown exception");
                } catch (ExecutionException ex) {
                    Assert.assertNotNull(ex.getCause(), "cause of ExecutionException should not be null");
                    Assert.assertTrue(ex.getCause() instanceof RuntimeException);
                }
            } else {
                final long executeCount = workerFuture.get(60, TimeUnit.SECONDS);
                Assert.assertEquals(executeCount, i, "result mismatch");
            }
        }
        workerExecutor.shutdown();

        Assert.assertTrue(max.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max.get());

        Assert.assertEquals(state.getCurrent(blockId).intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked(blockId), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue(blockId).intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

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
    private static class ImapWorker implements Worker<Long> {
        /** Command start time. */
        private long commandStartTime;
        /** Worker execute count. */
        private int executeCount;
        /** Command total latency. */
        private long totalLatency;
        /** Imap worker state. */
        private ImapWorkerState state;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Max workers detected at any time. */
        private final AtomicInteger max;
        /** Shared counter to track worker concurrency. */
        private AtomicInteger sharedCounter;

        /**
         * Constructor of ImapWorker.
         *
         * @param blockId block Id
         * @param blockManagerState BlockManagerMaxCount.State
         * @param max workers detected at any time.
         * @param sharedCounter Shared counter to track worker concurrency.
         */
        ImapWorker(final String blockId, final BlockManagerMaxCount.SharedState blockManagerState, final AtomicInteger max,
                final AtomicInteger sharedCounter) {
            this.state = ImapWorkerState.PICK_COMMAND;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, blockManagerState));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.max = max;
            this.sharedCounter = sharedCounter;
        }

        @Override
        public boolean execute() throws WorkerException {
            if (!blockManager.isAllowed()) {
                return false;
            }
            sharedCounter.incrementAndGet();
            switch (state) {
            case PICK_COMMAND:
                state = ImapWorkerState.EXECUTE_COMMAND;
                commandStartTime = System.currentTimeMillis();
                sharedCounter.decrementAndGet();
                return false;
            case EXECUTE_COMMAND:
                // store max value
                int maxCur = max.get();
                int current = sharedCounter.get();
                if (current > maxCur) {
                    max.compareAndSet(maxCur, current);
                }

                try {
                    // Create contention
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    // ignore.
                }

                totalLatency = System.currentTimeMillis() - commandStartTime;
                executeCount++;
                sharedCounter.decrementAndGet();
                return totalLatency > 100;
            default:
                sharedCounter.decrementAndGet();
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
        public Long getData() {
            return totalLatency;
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
     * Fake Imap worker used to test mutilple workers in one thread.
     *
     * @author kaituo
     *
     */
    private static class ImapWorkerAsync implements Worker<Long> {

        /** Worker Id. */
        private int workerId;
        /** Command start time. */
        private long commandStartTime;
        /** Worker execute count. */
        private long executeCount;
        /** Command total latency. */
        private long totalLatency;
        /** COmmand total count. */
        private int commandCount;
        /** Imap worker state. */
        private ImapWorkerState state;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Max workers detected at any time. */
        private final AtomicInteger max;
        /** Clock. */
        private Clock clock;
        /** Shared counter to track worker concurrency. */
        private AtomicInteger sharedCounter;
        /** Whether the shared counter has been incremented. */
        private boolean incremented;

        /**
         * Constructor of ImapWorker.
         *
         * @param workerId worker Id
         * @param blockId block Id
         * @param state BlockManagerMaxCount.State
         * @param max maximum value of threads
         * @param sharedCounter Shared counter to track worker concurrency.
         */
        ImapWorkerAsync(final int workerId, final String blockId, final BlockManagerMaxCount.SharedState state, final AtomicInteger max,
                @Nonnull final AtomicInteger sharedCounter) {
            this.workerId = workerId;
            this.state = ImapWorkerState.PICK_COMMAND;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.max = max;
            this.clock = Clock.systemUTC();
            this.sharedCounter = sharedCounter;
        }

        @Override
        public boolean execute() throws WorkerException {
            blockManager.setPersistBlockOneTimeAfterExit();
            if (!blockManager.isAllowed()) {
                return false;
            }
            if (!incremented) {
                sharedCounter.incrementAndGet();
                incremented = true;
            }
            if (clock.millis() - commandStartTime < 1) {
                return false;
            }
            switch (state) {
            case PICK_COMMAND:
                state = ImapWorkerState.EXECUTE_COMMAND;
                commandStartTime = clock.millis();
                commandCount++;
                return false;
            case EXECUTE_COMMAND:
                if (executeCount == workerId) {
                    sharedCounter.decrementAndGet();
                    return true;
                }
                executeCount++;
                // store max value
                int expect = max.get();
                int current = sharedCounter.get();
                if (current > expect) {
                    max.compareAndSet(expect, current);
                }
                long latency = clock.millis() - commandStartTime;
                totalLatency += latency;
                state = ImapWorkerState.PICK_COMMAND;
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
        public Long getData() {
            return executeCount;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return this.blockManager;
        }

        @Override
        public String toString() {
            return "ImapWorkerAsync";
        }

        @Override
        public void cleanup() {
            this.sharedCounter = null;
        }
    }

    /**
     * Tests multiple BlockManagerMaxCount in 6 worker threads, to validate that 2 different blockId get blocked at the same rate.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @Test
    public void testMultipleWorkersAsync() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(10);
        final List<ImapWorkerAsync> workers = new ArrayList<>();
        final List<WorkerFuture<Long>> futures = new ArrayList<WorkerFuture<Long>>();
        int workerCount = 100;
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        final AtomicInteger max1 = new AtomicInteger(0);
        final AtomicInteger max2 = new AtomicInteger(0);
        final AtomicInteger counter1 = new AtomicInteger(0);
        final AtomicInteger counter2 = new AtomicInteger(0);
        for (int i = 0; i < workerCount; i++) {
            final ImapWorkerAsync imapWorker;
            if (i % 2 == 0) {
                imapWorker = new ImapWorkerAsync(i, "1560", state, max1, counter1);
            } else {
                imapWorker = new ImapWorkerAsync(i, "1570", state, max2, counter2);
            }
            workers.add(imapWorker);
            futures.add(exec.submit(imapWorker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            final int executeCount = futures.get(i).get(60, TimeUnit.SECONDS).intValue();
            Assert.assertEquals(executeCount, i, "Execute count mismatch.");

        }

        exec.shutdown();

        Assert.assertTrue(max1.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max1.get());
        Assert.assertTrue(max2.get() <= MAX_PER_BLOCKID, "maximum number of workers detected exceeded the limit:" + max2.get());

        long totalLatency = 0;
        int totalCommand = 0;
        for (int i = 0; i < workerCount; i++) {
            ImapWorkerAsync worker = workers.get(i);
            totalLatency += worker.totalLatency;
            totalCommand += worker.commandCount;

        }
        double latency = totalLatency / totalCommand;
        Assert.assertTrue(latency < workerCount / 2, "Latency should less than worker count divide by 2");

        Assert.assertEquals(state.getCurrent("1560").intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked("1560"), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue("1560").intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

        Assert.assertEquals(state.getCurrent("1570").intValue(), 0, "counter should be reset to 0 after all worker execution is done");
        Assert.assertEquals(state.getUnblocked("1570"), 0L, "total number of unblocked workers should be 0 after all worker is done");
        Assert.assertEquals(state.getInQueue("1570").intValue(), 0, "total number of unblocked workers should be 0 after all worker is done");

    }

    /**
     * Method to test the logic in getWorkerStatsMap.
     *
     * @throws WorkerException never
     */
    @Test
    public void testGetWorkerStatsMap() throws WorkerException {
        BlockManagerMaxCount.SharedState state = new BlockManagerMaxCount.SharedState();
        state.workerCreated("blockId1");
        state.workerCreated("blockId2");
        state.workerCreated("blockId2");
        state.increment("blockId1");
        state.increment("blockId1");
        state.increment("blockId2");
        state.increment("blockId2");
        state.increment("blockId2");
        state.incrUnblocked("blockId1");
        state.incrUnblocked("blockId1");
        Map<String, BlockIdStats> blockIdMap = state.getBlockIdStatsMap();
        Assert.assertNotNull(blockIdMap, "Map should not be null");
        Assert.assertEquals(blockIdMap.keySet().size(), 2, "There should be only one key in the map");
        Assert.assertEquals(blockIdMap.values().size(), 2, "There should be only one BlockIdStats object value for the key");
        Assert.assertEquals(blockIdMap.get("blockId1").getCount(), 2, "The count value should be 1");
        Assert.assertEquals(blockIdMap.get("blockId2").getCount(), 3, "The count value should be 1");
        Assert.assertEquals(blockIdMap.get("blockId1").getInQueue(), 1, "The Inqueue value should be 2");
        Assert.assertEquals(blockIdMap.get("blockId2").getInQueue(), 2, "The count value should be 1");
        Assert.assertEquals(blockIdMap.get("blockId1").getUnblocked(), 2, "The unblocked value should be 1");
        Assert.assertEquals(blockIdMap.get("blockId2").getUnblocked(), 0, "The count value should be 0s");
    }

    /**
     * Test worker to throw checked exception in execute().
     */
    public class WorkerWithCheckedException implements Worker<Long> {

        /** Worker execute count. */
        private long executeCount;
        /** Imap worker state. */
        private Exception exception;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Shared state. */
        private final AtomicInteger sharedCounter;
        /** Maximum number of workers concurrently running. */
        private AtomicInteger max;

        /**
         * Constructor of WorkerWithCheckedException.
         *
         * @param blockId block Id
         * @param state BlockManager state
         * @param max workers detected at any time.
         * @param sharedCounter shared counter to track worker concurrency.
         */
        public WorkerWithCheckedException(@Nonnull final String blockId, @Nonnull final BlockManagerMaxCount.SharedState state,
                @Nonnull final AtomicInteger max, @Nonnull final AtomicInteger sharedCounter) {
            this.executeCount = 0;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.sharedCounter = sharedCounter;
            this.max = max;
        }

        @Override
        public boolean execute() throws WorkerException {
            if (!blockManager.isAllowed()) {
                return false;
            }
            sharedCounter.incrementAndGet();
            int maxCur = max.get();
            // store max value
            int current = sharedCounter.get();
            if (current > maxCur) {
                max.compareAndSet(maxCur, current);
            }
            executeCount++;
            exception = new Exception("dummy exception");
            sharedCounter.decrementAndGet();
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
        public Long getData() {
            return executeCount;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return this.blockManager;
        }

        @Override
        public void cleanup() {
            max = null;
        }
    }

    /**
     * Test worker to throw unchecked exception in execute().
     */
    public class WorkerWithUncheckedException implements Worker<Long> {

        /** Worker execute count. */
        private long executeCount;
        /** exception to be thrown. */
        private RuntimeException exception;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Shared state. */
        private final AtomicInteger sharedCounter;
        /** Maximum number of workers concurrently running. */
        private final AtomicInteger max;

        /**
         * Constructor of WorkerWithUncheckedException.
         *
         * @param blockId block Id
         * @param state BlockManager state
         * @param max workers detected at any time.
         * @param sharedCounter shared counter.
         */
        public WorkerWithUncheckedException(@Nonnull final String blockId, @Nonnull final BlockManagerMaxCount.SharedState state,
                @Nonnull final AtomicInteger max, @Nonnull final AtomicInteger sharedCounter) {
            this.executeCount = 0;
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.sharedCounter = sharedCounter;
            this.max = max;
        }

        @Override
        public boolean execute() throws WorkerException {
            if (!blockManager.isAllowed()) {
                return false;
            }
            sharedCounter.incrementAndGet();
            int maxCur = max.get();
            // store max value
            int current = sharedCounter.get();
            if (current > maxCur) {
                max.compareAndSet(maxCur, current);
            }
            executeCount++;
            exception = new RuntimeException("dummy exception");
            sharedCounter.decrementAndGet();
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
        public Long getData() {
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
     * Test worker to throw checked exception in execute().
     */
    public class WorkerWithCheckedExceptionAsync implements Worker<Long> {

        /** Imap worker state. */
        private Exception exception;
        /** execution count. */
        private int executionCount;
        /** worker id. */
        private int workerId;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Shared state. */
        private final AtomicInteger sharedCounter;
        /** Maximum number of workers concurrently running. */
        private final AtomicInteger max;
        /** boolean to indicate whether the shared counter has been incremented. */
        private boolean incremented;

        /**
         * Constructor of AysncWorkerWithCheckedException.
         *
         * @param workerId worker sequence id.
         * @param blockId block Id
         * @param state BlockManager state
         * @param max workers detected at any time.
         * @param sharedCounter shared counter to track worker concurrency.
         */
        public WorkerWithCheckedExceptionAsync(final int workerId, @Nonnull final String blockId,
                @Nonnull final BlockManagerMaxCount.SharedState state, @Nonnull final AtomicInteger max, @Nonnull final AtomicInteger sharedCounter) {
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.sharedCounter = sharedCounter;
            this.max = max;
            this.executionCount = 0;
            this.workerId = workerId;
        }

        @Override
        public boolean execute() throws WorkerException {
            blockManager.setPersistBlockOneTimeAfterExit();
            if (!blockManager.isAllowed()) {
                return false;
            }
            if (!incremented) {
                sharedCounter.incrementAndGet();
                incremented = true;
            }
            int maxCur = max.get();
            // store max value
            int current = sharedCounter.get();
            if (current > maxCur) {
                max.compareAndSet(maxCur, current);
            }
            if (executionCount == workerId) {
                exception = new Exception("dummy exception");
                sharedCounter.decrementAndGet();
                throw new WorkerException(exception);
            }
            executionCount++;
            return false;
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
        public Long getData() {
            return null;
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
     */
    public class WorkerWithUncheckedExceptionAsync implements Worker<Long> {

        /** Imap worker state. */
        private RuntimeException exception;
        /** execution count. */
        private int executionCount;
        /** worker id. */
        private int workerId;
        /** Block manager state. */
        private final WorkerBlockManager blockManager;
        /** Shared state. */
        private final AtomicInteger sharedCounter;
        /** Maximum number of workers concurrently running. */
        private final AtomicInteger max;
        /** boolean to indicate whether the shared counter has been incremented. */
        private boolean incremented;

        /**
         * Constructor of WorkerWithCheckedException.
         *
         * @param workerId worker sequence id.
         * @param blockId block Id
         * @param state BlockManager state
         * @param max workers detected at any time.
         * @param sharedCounter shared counter to track worker concurrency.
         */
        public WorkerWithUncheckedExceptionAsync(final int workerId, @Nonnull final String blockId,
                @Nonnull final BlockManagerMaxCount.SharedState state, @Nonnull final AtomicInteger max, @Nonnull final AtomicInteger sharedCounter) {
            Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
            blockManagerStates.add(new BlockManagerMaxCount.State(blockId, MAX_PER_BLOCKID, state));
            this.blockManager = new BlockManagerMaxCount(blockManagerStates);
            this.sharedCounter = sharedCounter;
            this.max = max;
            this.executionCount = 0;
            this.workerId = workerId;
        }

        @Override
        public boolean execute() throws WorkerException {
            blockManager.setPersistBlockOneTimeAfterExit();
            if (!blockManager.isAllowed()) {
                return false;
            }
            if (!incremented) {
                sharedCounter.incrementAndGet();
                incremented = true;
            }
            int maxCur = max.get();
            // store max value
            int current = sharedCounter.get();
            if (current > maxCur) {
                max.compareAndSet(maxCur, current);
            }
            if (executionCount == workerId) {
                exception = new RuntimeException("dummy exception");
                sharedCounter.decrementAndGet();
                throw exception;
            }

            executionCount++;
            return false;
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
        public Long getData() {
            return null;
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

    /** maximum number of workers allowed per blockId. **/
    private static final Long MAX_PER_BLOCKID = new Long(3);
}
