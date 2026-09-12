package io.github.spencerpark.jupyter.channels;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;
import java.util.logging.Logger;

public class Loop extends Thread {
    private final Logger logger;

    private volatile boolean running = false;
    private final LongSupplier loopBody;

    private final AtomicReference<Runnable> onCloseCb = new AtomicReference<>();
    private final AtomicReference<ToLongFunction<Throwable>> onErrorCb = new AtomicReference<>();
    private final Queue<Runnable> runNextQueue;

    public Loop(String name, long sleep, Runnable target) {
        this(name, () -> {
            target.run();
            return sleep;
        });
    }

    public Loop(String name, LongSupplier target) {
        super(name);

        this.loopBody = target;

        this.runNextQueue = new LinkedBlockingQueue<>();

        this.logger = Logger.getLogger("Loop-" + name);
    }

    public void onClose(Runnable callback) {
        this.onCloseCb.updateAndGet(old -> old == null
                ? callback
                : () -> {
                    old.run();
                    callback.run();
                });
    }

    public void onError(ToLongFunction<Throwable> callback) {
        this.onErrorCb.updateAndGet(old -> {
            if (old == null)
                return callback;

            // Adding a second handler will only be invoked if the
            // previous one throws (or rethrows) the incoming exception.
            // The callback is invoked with the rethrown exception.
            return t -> {
                try {
                    return old.applyAsLong(t);
                } catch (Throwable tPrime) {
                    return callback.applyAsLong(tPrime);
                }
            };
        });
    }

    public void doNext(Runnable next) {
        this.runNextQueue.offer(next);
    }

    @Override
    public void run() {
        while (this.running) {
            long sleep;
            try {
                // Run the loop body
                sleep = this.loopBody.getAsLong();

                // Run all queued tasks
                this.runQueuedTasks();
            } catch (Throwable t) {
                ToLongFunction<Throwable> errorHandler = this.onErrorCb.get();
                if (errorHandler != null)
                    sleep = errorHandler.applyAsLong(t);
                else
                    throw t;
            }

            if (sleep > 0) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    this.logger.info("Loop interrupted. Stopping...");
                    this.running = false;
                }
            } else if (sleep < 0) {
                this.logger.info("Loop interrupted by a negative sleep request. Stopping...");
                this.running = false;
            }
        }

        this.logger.info("Running loop shutdown callback.");

        Runnable closeCallback = this.onCloseCb.get();
        if (closeCallback != null)
            closeCallback.run();
        this.onCloseCb.set(null);

        this.logger.info("Loop stopped.");
    }

    private void runQueuedTasks() {
        Runnable next;
        while ((next = this.runNextQueue.poll()) != null)
            next.run();
    }

    @Override
    public synchronized void start() {
        this.logger.info("Loop starting...");

        this.running = true;
        super.start();

        this.logger.info("Loop started.");
    }

    public void shutdown() {
        this.running = false;
        this.logger.info("Loop shutdown.");
    }
}
