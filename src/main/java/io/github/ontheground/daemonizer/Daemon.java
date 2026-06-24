package io.github.ontheground.daemonizer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * A single-queue, single-thread event processor with sequential processing and backpressure.
 *
 * <p>Events are processed one at a time in FIFO order. Producers that call {@link #pushEvent}
 * block when the queue is full, providing natural backpressure analogous to a full Go buffered channel.
 *
 * <p>Closing the daemon drains all queued events before terminating.
 *
 * @param <E> the type of events to process
 */
public class Daemon<E> implements AutoCloseable {

    private static final Object POISON_PILL = new Object();

    private final BlockingQueue<Object> eventQueue;
    private final BiConsumer<E, Thread> handleEvent;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Thread loopThread;

    /**
     * Creates a new daemon and immediately starts its background processing thread.
     *
     * @param bufferSize   maximum number of events the queue can hold before blocking producers
     * @param handleEvent  handler called for each event; receives the event and the loop thread
     *                     (which can be inspected for interruption status)
     */
    public Daemon(int bufferSize, BiConsumer<E, Thread> handleEvent) {
        this.eventQueue = new LinkedBlockingQueue<>(bufferSize);
        this.handleEvent = handleEvent;
        this.loopThread = Thread.ofVirtual().name("Daemon-Loop-Thread").unstarted(this::loop);
        this.loopThread.start();
    }

    private void loop() {
        try {
            while (true) {
                Object raw = eventQueue.take();
                if (raw == POISON_PILL) {
                    for (Object remaining : eventQueue) {
                        if (remaining != POISON_PILL) {
                            @SuppressWarnings("unchecked")
                            E event = (E) remaining;
                            handleEvent.accept(event, Thread.currentThread());
                        }
                    }
                    break;
                }
                @SuppressWarnings("unchecked")
                E event = (E) raw;
                handleEvent.accept(event, Thread.currentThread());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Pushes an event onto the queue, blocking if the queue is full until space is available.
     * Equivalent to sending on a full Go buffered channel.
     *
     * @param event the event to enqueue
     * @return {@code true} if the event was accepted; {@code false} if the daemon is closed
     * @throws InterruptedException if the calling thread is interrupted while waiting for space
     */
    public boolean pushEvent(E event) throws InterruptedException {
        if (isClosed.get()) return false;
        eventQueue.put(event);
        return true;
    }

    /**
     * Pushes an event onto the queue without blocking.
     *
     * @param event the event to enqueue
     * @return {@code true} if the event was accepted; {@code false} if the queue is full or closed
     */
    public boolean tryPushEvent(E event) {
        if (isClosed.get()) return false;
        return eventQueue.offer(event);
    }

    /**
     * Gracefully shuts down the daemon. Blocks until all queued events have been processed.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting for the loop thread to finish
     */
    @Override
    public void close() throws InterruptedException {
        if (!isClosed.compareAndSet(false, true)) return;
        eventQueue.put(POISON_PILL);
        loopThread.join();
    }
}
