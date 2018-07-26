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
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for WorkerExecutorService.
 *
 * @author hinrichs
 */
public class WorkerExecutorServiceTest {

    /** Number of workers for the test. */
    private static final int NUM_WORKERS = 80;

    /**
     * Basic test which submits a worker and verifies that the Future returns the worker's data.
     *
     * @throws Exception not expected
     */
    @Test
    public void testBasicSubmit() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(10);
        final AtomicBoolean done = new AtomicBoolean(true);
        final Worker<Integer> worker = new TestWorker(done);
        final WorkerFuture<Integer> future = executor.submit(worker, new WorkerExceptionHandlerImpl());
        Assert.assertEquals(future.get(60, TimeUnit.SECONDS), new Integer(1), "Should have incremented the value a single time in the worker");
    }

    /**
     * Test if the number of thread is 1, multiple workers can be submitted, and each one of them is successfully executed.
     *
     * @throws Exception not expected
     */
    @Test
    public void testSingleThread() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(1);
        final AtomicBoolean done = new AtomicBoolean(true);
        final Worker<Integer> worker = new TestWorker(done);
        final WorkerFuture<Integer> future = executor.submit(worker, new WorkerExceptionHandlerImpl());
        Assert.assertEquals(future.get(60, TimeUnit.SECONDS), new Integer(1), "Should have incremented the value a single time in the worker");

        // the worker manager thread sleeps for 100 ms, this will never fail when run in non debug mode, add a sleep of 200 ms to see if it is really
        // broken.
        final Worker<Integer> worker2 = new TestWorker(done);
        final WorkerFuture<Integer> future2 = executor.submit(worker2, new WorkerExceptionHandlerImpl());
        Assert.assertEquals(future2.get(1, TimeUnit.SECONDS), new Integer(1), "Should have incremented the value a single time in the worker");

        final Worker<Integer> worker3 = new TestWorker(done);
        final WorkerFuture<Integer> future3 = executor.submit(worker3, new WorkerExceptionHandlerImpl());
        Assert.assertEquals(future3.get(1, TimeUnit.SECONDS), new Integer(1), "Should have incremented the value a single time in the worker");
    }

    /**
     * Tests running enough workers that we have multiple workers queued for each thread, and makes sure some of them complete at different times.
     * This test verifies that all queued workers get executed as expected.
     *
     * @throws Exception not expected
     */
    @Test
    public void testManyWorkers() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(10);
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean done2 = new AtomicBoolean(false);
        final List<Worker<Integer>> workers = new ArrayList<>();
        final List<WorkerFuture<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < NUM_WORKERS; i++) {
            final Worker<Integer> worker = new TestWorker(i % 2 == 0 ? done : done2);
            workers.add(worker);
            futures.add(executor.submit(worker, new WorkerExceptionHandlerImpl()));
        }

        Thread.sleep(500);

        done.set(true);

        Thread.sleep(500);

        done2.set(true);

        for (int i = 0; i < NUM_WORKERS; i++) {
            Assert.assertTrue(futures.get(i).get(60, TimeUnit.SECONDS) > 1, "All workers should have been executed more than once");
        }
    }

    /**
     * Tests when a worker throws an exception during execution.
     *
     * @throws Exception not expected
     */
    @Test(expectedExceptions = ExecutionException.class, expectedExceptionsMessageRegExp = "java.lang.Exception: Failed")
    public void testWorkerWithException() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(10);
        final Worker<Integer> worker = new TestExceptionWorker();
        final WorkerFuture<Integer> future = executor.submit(worker, new WorkerExceptionHandlerImpl());
        future.get(60, TimeUnit.SECONDS);
    }

    /**
     * Tests when a worker throws a runtime exception during execution.
     *
     * @throws Exception not expected
     */
    @Test(expectedExceptions = ExecutionException.class, expectedExceptionsMessageRegExp = "java.lang.RuntimeException: Failed")
    public void testWorkerRuntimeException() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(10);
        final Worker<Integer> worker = new TestRuntimeExceptionWorker();
        final WorkerFuture<Integer> future = executor.submit(worker, new WorkerExceptionHandlerImpl());
        future.get(60, TimeUnit.SECONDS);
    }

    /**
     * Tests when a worker throws a runtime exception during execution.
     *
     * @throws Exception not expected
     */
    @Test
    public void testWorkerWithoutBlockManager() throws Exception {
        final WorkerExecutorService executor = new WorkerExecutorService(10);
        final Worker<Integer> worker = new TestNoBlockManagerWorker();
        try {
            executor.submit(worker, new WorkerExceptionHandlerImpl());
            Assert.fail("should have thrown exception.");
        } catch (WorkerException ex) {
            Assert.assertEquals(ex.getMessage(), "getBlockManager() returned null.");
        }
    }

    /**
     * Tests with good workers.
     *
     * @throws InterruptedException
     *             not expected
     * @throws ExecutionException
     *             not expected
     * @throws WorkerException
     *             not expected
     * @throws TimeoutException
     *             not expected
     */
    @Test
    public void testGoodWorker() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(11000);
        final WorkerFuture<Integer> future = exec.submit(worker, new WorkerExceptionHandlerImpl());
        final Integer integer = future.get(60, TimeUnit.SECONDS);
        Assert.assertEquals(integer, new Integer(1));
        exec.shutdown();
    }

    /**
     * Tests worker with errors.
     *
     * @throws InterruptedException
     *             not expected
     * @throws ExecutionException
     *             not expected
     * @throws WorkerException
     *             not expected
     * @throws TimeoutException
     *             not expected
     */
    @Test
    public void testWorkerWithHasErrors() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(11000);
        final WorkerFuture<Integer> future = exec.submit(workerException, new WorkerExceptionHandlerImpl());
        try {
            future.get(60, TimeUnit.SECONDS);
            Assert.fail("Exception expected.");
        } catch (final java.util.concurrent.ExecutionException e) {
            Assert.assertEquals(e.getClass().getName(), java.util.concurrent.ExecutionException.class.getName());
        }
        exec.shutdown();
    }

    /**
     * Tests worker that throws a WorkerException.
     *
     * @throws InterruptedException
     *             not expected
     * @throws ExecutionException
     *             not expected
     * @throws WorkerException
     *             not expected
     * @throws TimeoutException
     *             not expected
     */
    @Test
    public void testWorkerWithWorkerException() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(11000);
        final WorkerFuture<Integer> future = exec.submit(workerWorkerException, new WorkerExceptionHandlerImpl());
        try {
            future.get(60, TimeUnit.SECONDS);
            Assert.fail("Exception expected.");
        } catch (final java.util.concurrent.ExecutionException e) {
            Assert.assertEquals(e.getClass().getName(), java.util.concurrent.ExecutionException.class.getName());
        }
        exec.shutdown();
    }

    /**
     * Runs a test with 32 workers.
     *
     * @throws InterruptedException
     *             not expected
     * @throws ExecutionException
     *             not expected
     * @throws WorkerException
     *             not expected
     * @throws TimeoutException
     *             not expected
     */
    @Test
    public void test32Workers() throws InterruptedException, ExecutionException, WorkerException, TimeoutException {
        final WorkerExecutorService exec = new WorkerExecutorService(32);
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        for (int i = 0; i < 32; i++) {
            futures.add(exec.submit(worker, new WorkerExceptionHandlerImpl()));
        }
        for (final WorkerFuture<Integer> future : futures) {
            final Integer integer = future.get(60, TimeUnit.SECONDS);
            Assert.assertEquals(integer, new Integer(1));
        }
        Assert.assertEquals(futures.size(), 32);
        exec.shutdown();
    }

    /**
     * Tests canceling 1000 workers.
     *
     * @throws InterruptedException not expected
     * @throws ExecutionException not expected
     * @throws WorkerException not expected
     */
    @Test
    public void testCancel1000Workers() throws InterruptedException, ExecutionException, WorkerException {
        final WorkerExecutorService exec = new WorkerExecutorService(11000);
        final List<WorkerFuture<Integer>> futures = new ArrayList<WorkerFuture<Integer>>();
        for (int i = 0; i < 1000; i++) {
            futures.add(exec.submit(workerEndless, new WorkerExceptionHandlerImpl()));
        }
        Assert.assertEquals(futures.size(), 1000);

        for (final WorkerFuture<Integer> future : futures) {
            try {
                future.get(1, TimeUnit.MILLISECONDS);
                Assert.fail("Timeout exception was expected.");
            } catch (final java.util.concurrent.CancellationException e) {
                Assert.fail("Timeout exception was expected.");
            } catch (final TimeoutException e) {
                Assert.assertEquals(e.getClass().getName(), TimeoutException.class.getName());
            }

        }

        for (final WorkerFuture<Integer> future : futures) {
            if (!future.isDone()) {
                future.cancel(false);
            }
        }
        for (final WorkerFuture<Integer> future : futures) {
            if (!future.isDone()) {
                Assert.fail("IsDone was expected.");
            }

            if (!future.isCancelled()) {
                Assert.fail("isCancelled was expected.");
            }
        }

        for (final WorkerFuture<Integer> future : futures) {
            try {
                future.get(1, TimeUnit.MILLISECONDS);
                Assert.fail("Cancelation exception was expected.");
            } catch (final java.util.concurrent.CancellationException e) {
                Assert.assertEquals(e.getClass().getName(), java.util.concurrent.CancellationException.class.getName());
            } catch (final TimeoutException e) {
                Assert.fail("Cancelation exception was expected.");
            }

        }
        exec.shutdown();
    }

    /**
     * Tests the WorkerException constructor.
     */
    @Test
    public void testWorkerException() {
        new WorkerException();
        new WorkerException("", new Exception());
        new WorkerException("");
        new WorkerException(new Exception());
    }

    /**
     * Test worker class.
     *
     * @author hinrichs
     */
    private class TestWorker implements Worker<Integer> {
        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        /** Boolean that, when set to true, will stop the execution of the worker. */
        private final AtomicBoolean done;

        /** Internal count of calls to execute. */
        private Integer count = 0;

        /**
         * @param done boolean to indicate if the worker is done
         */
        TestWorker(final AtomicBoolean done) {
            this.done = done;
        }

        @Override
        public boolean execute() throws WorkerException {
            count++;
            return done.get();
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
            return count;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }

    }

    /**
     * Test worker class.
     *
     * @author hinrichs
     */
    private class TestExceptionWorker implements Worker<Integer> {
        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            return true;
        }

        @Override
        public Exception getCause() {
            return new Exception("Failed");
        }

        @Override
        public boolean hasErrors() {
            return true;
        }

        @Override
        public Integer getData() {
            return null;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }

    }

    /**
     * Test worker class.
     *
     * @author hinrichs
     */
    private class TestRuntimeExceptionWorker implements Worker<Integer> {
        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            throw new RuntimeException("Failed");
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
            return null;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }

    }

    /**
     * Test worker class.
     *
     * @author hinrichs
     */
    private class TestNoBlockManagerWorker implements Worker<Integer> {

        @Override
        public boolean execute() throws WorkerException {
            throw new RuntimeException("Failed");
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
            return null;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return null;
        }

    }

    /**
     * test worker.
     */
    private final Worker<Integer> worker = new Worker<Integer>() {

        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            return true;
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
            return new Integer(1);
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }

    };

    /**
     * Test worker that returns an exception.
     */
    private final Worker<Integer> workerException = new Worker<Integer>() {

        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            return true;
        }

        @Override
        public Exception getCause() {
            return new Exception("Exception to test ExcecutorService.");
        }

        @Override
        public boolean hasErrors() {
            return true;
        }

        @Override
        public Integer getData() {
            return new Integer(1);
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }

    };

    /**
     * Test worker that never completes.
     */
    private final Worker<Integer> workerEndless = new Worker<Integer>() {

        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                // Ignore
            }
            return false;
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
            return null;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }
    };

    /**
     * Test worker that throws a WorkerException.
     */
    private final Worker<Integer> workerWorkerException = new Worker<Integer>() {

        /** set of states. */
        private Set<BlockManagerMaxCount.State> blockManagerStates = new HashSet<>();
        /** block manager instance. */
        private WorkerBlockManager blockManager = new BlockManagerMaxCount(blockManagerStates);

        @Override
        public boolean execute() throws WorkerException {
            throw new WorkerException("Test the Excecutor Service with Exception", new Exception("Test Exception"));
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
            return null;
        }

        @Override
        public WorkerBlockManager getBlockManager() {
            return blockManager;
        }
    };
}
