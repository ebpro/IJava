package io.github.spencerpark.jupyter.channels;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class LoopTest {

    @Test
    public void loopRunsBodyUntilShutdown() throws Exception {
        AtomicLong iterations = new AtomicLong();
        Loop loop = new Loop("test-loop", () -> {
            iterations.incrementAndGet();
            return 1;
        });
        loop.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (iterations.get() < 3 && System.currentTimeMillis() < deadline)
            Thread.sleep(1);

        loop.shutdown();
        loop.join(5000);

        Assert.assertFalse("Loop thread should have stopped", loop.isAlive());
        Assert.assertTrue("Loop body should have run multiple times", iterations.get() >= 3);
    }

    @Test
    public void doNextRunsQueuedTaskOnLoopThread() throws Exception {
        AtomicBoolean ranOnLoopThread = new AtomicBoolean();
        CountDownLatch taskDone = new CountDownLatch(1);
        Loop loop = new Loop("test-do-next", () -> 1);
        loop.start();

        loop.doNext(() -> {
            ranOnLoopThread.set(Thread.currentThread() == loop);
            taskDone.countDown();
        });

        Assert.assertTrue("Queued task should have run", taskDone.await(5, TimeUnit.SECONDS));
        Assert.assertTrue("Queued task should run on the loop thread", ranOnLoopThread.get());

        loop.shutdown();
        loop.join(5000);
        Assert.assertFalse("Loop thread should have stopped", loop.isAlive());
    }

    @Test
    public void onCloseCallbackInvokedWhenLoopStops() throws Exception {
        CountDownLatch closeDone = new CountDownLatch(1);
        Loop loop = new Loop("test-on-close", () -> 1);
        loop.onClose(closeDone::countDown);
        loop.start();
        loop.shutdown();
        loop.join(5000);

        Assert.assertFalse("Loop thread should have stopped", loop.isAlive());
        Assert.assertTrue("onClose callback should have run", closeDone.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void multipleOnCloseCallbacksAllInvoked() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        Loop loop = new Loop("test-on-close-chain", () -> 1);
        loop.onClose(first::countDown);
        loop.onClose(second::countDown);
        loop.start();
        loop.shutdown();
        loop.join(5000);

        Assert.assertFalse("Loop thread should have stopped", loop.isAlive());
        Assert.assertTrue("First onClose callback should have run", first.await(1, TimeUnit.SECONDS));
        Assert.assertTrue("Second onClose callback should have run", second.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void onErrorCallbackControlsSleepAfterFailure() throws Exception {
        AtomicLong iterations = new AtomicLong();
        AtomicBoolean errorSeen = new AtomicBoolean();
        Loop loop = new Loop("test-on-error", () -> {
            if (iterations.incrementAndGet() == 1)
                throw new IllegalStateException("boom");
            return 1;
        });
        loop.onError(t -> {
            errorSeen.set(t instanceof IllegalStateException);
            return 1;
        });
        loop.start();

        long deadline = System.currentTimeMillis() + 5000;
        while (iterations.get() < 3 && System.currentTimeMillis() < deadline)
            Thread.sleep(1);

        loop.shutdown();
        loop.join(5000);

        Assert.assertFalse("Loop thread should have stopped", loop.isAlive());
        Assert.assertTrue("onError should have received the thrown exception", errorSeen.get());
        Assert.assertTrue("Loop should continue after onError", iterations.get() >= 3);
    }

    @Test
    public void secondOnErrorHandlerReceivesRethrownException() throws Exception {
        AtomicBoolean firstSawOriginal = new AtomicBoolean();
        AtomicBoolean secondSawRethrown = new AtomicBoolean();
        Loop loop = new Loop("test-on-error-chain", () -> {
            throw new IllegalStateException("boom");
        });
        loop.onError(t -> {
            firstSawOriginal.set(t instanceof IllegalStateException);
            throw new RuntimeException("rethrown");
        });
        loop.onError(t -> {
            secondSawRethrown.set(t instanceof RuntimeException && "rethrown".equals(t.getMessage()));
            return -1;
        });
        loop.start();
        loop.join(5000);

        Assert.assertFalse("Loop should stop after negative sleep", loop.isAlive());
        Assert.assertTrue("First handler should have seen the original exception", firstSawOriginal.get());
        Assert.assertTrue("Second handler should receive the rethrown exception", secondSawRethrown.get());
    }

    @Test
    public void negativeSleepStopsLoop() throws Exception {
        Loop loop = new Loop("test-negative-sleep", () -> -1);
        loop.start();
        loop.join(5000);

        Assert.assertFalse("Loop should stop on negative sleep", loop.isAlive());
    }
}
