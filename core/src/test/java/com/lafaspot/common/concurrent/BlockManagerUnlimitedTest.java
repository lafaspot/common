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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for WorkerExecutorService.
 *
 */
public final class BlockManagerUnlimitedTest {

    /**
     * Tests multiple workers in 10 worker threads, validate that max greater than 6.
     *
     * @throws InterruptedException never for this test
     * @throws ExecutionException never for this test
     * @throws WorkerException never for this test
     * @throws TimeoutException never for this test
     */
    @Test(invocationCount = 1)
    public void testWorkerManagerOneThreadWithMultipleWorkers() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(10);
        final List<ImapWorker> workers = new ArrayList<>();
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        int workerCount = 100;
        AtomicLong max = new AtomicLong(0);
        for (int i = 0; i < workerCount; i++) {
            final ImapWorker imapWorker = new ImapWorker(i, max);
            workers.add(imapWorker);
            futures.add(exec.submit(imapWorker, new WorkerExceptionHandlerImpl()));
        }
        for (int i = 0; i < workerCount; i++) {
            final int executeCount = futures.get(i).get(60, TimeUnit.SECONDS);
            Assert.assertEquals(executeCount, i, "Execute count mismatch.");

        }

        exec.shutdown();

        Assert.assertTrue(max.get() >= 3, "max does match expected: " + max.get());

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
    private static class ImapWorker implements Worker<Integer> {

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
        /** Max workers running at same time. */
        private static final AtomicLong CONCURRENT = new AtomicLong(0);
        /** Max workers detected at any time. */
        private final AtomicLong max;

        /**
         * Constructor of ImapWorker.
         *
         * @param workerId worker Id
         * @param max max count.
         */
        ImapWorker(final int workerId, final AtomicLong max) {
            this.workerId = workerId;
            this.state = ImapWorkerState.PICK_COMMAND;
            this.blockManager = BlockManagerUnlimited.UNLIMITED;
            this.max = max;
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
                    try {
                        Long current = CONCURRENT.incrementAndGet();
                        Thread.sleep(3);
                        Long expect = max.get();
                        // store max value
                        if (current > expect) {
                            max.compareAndSet(expect, current);
                        }
                        CONCURRENT.decrementAndGet();

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
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
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public boolean hasErrors() {
            // TODO Auto-generated method stub
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
    }
}
