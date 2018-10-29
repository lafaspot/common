package com.lafaspot.common.concurrent.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.lafaspot.common.concurrent.BlockManagerMaxCount;
import com.lafaspot.common.concurrent.Worker;
import com.lafaspot.common.concurrent.WorkerBlockManager;
import com.lafaspot.common.concurrent.WorkerException;
import com.lafaspot.common.concurrent.WorkerFuture;

/**
 * Unit tests for {@code WorkerWrapper}.
 *
 * @author davisthomas
 *
 */
public class WorkerWrapperTest {

    /**
     * Test cancellation of a worker.
     *
     * @throws WorkerException
     */
    @Test
    public void testCancellation() throws WorkerException {
        final WorkerWrapper wrapper = new WorkerWrapper<>(worker);
        wrapper.cancel(false);
        wrapper.execute();
        final WorkerStats stats = wrapper.getStat();
        Assert.assertTrue(stats.getStatsAsString().contains("WorkerCancelCount=1"));
        WorkerFuture future = wrapper.getFuture();
        try {
            future.get(1, TimeUnit.MILLISECONDS);
            Assert.fail("Cancelation exception was expected.");
        } catch (final CancellationException e) {
            Assert.assertEquals(e.getClass().getName(), java.util.concurrent.CancellationException.class.getName());
        } catch (final TimeoutException | ExecutionException | InterruptedException e) {
            Assert.fail("Cancelation exception was expected.");
        }
    }

    /**
     * Test worker done without exception.
     *
     * @throws WorkerException
     * @throws ExecutionException
     * @throws InterruptedException
     */
    @Test
    public void testWorkerDoneWithoutException() throws WorkerException, InterruptedException, ExecutionException {
        final WorkerWrapper wrapper = new WorkerWrapper<>(worker);
        wrapper.execute();
        WorkerFuture future = wrapper.getFuture();
        Integer val = (Integer) future.get();
        Assert.assertEquals(val.intValue(), 1);
    }

    /**
     * Test worker done with exception.
     */
    @Test
    public void testWorkerDone() throws WorkerException, InterruptedException, ExecutionException {
        final WorkerWrapper wrapper = new WorkerWrapper<>(workerException);
        wrapper.execute();
        WorkerFuture future = wrapper.getFuture();
        try {
            future.get();
            Assert.fail("Exception should have been thrown");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "java.lang.Exception: Exception to test ExcecutorService.");
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

        @Override
        public void cleanup() {

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

        @Override
        public void cleanup() {

        }

    };

}
