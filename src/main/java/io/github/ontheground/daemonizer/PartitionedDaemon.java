package io.github.ontheground.daemonizer;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * A partitioned event processor backed by N {@link Daemon} instances, one per partition.
 *
 * <p>Events are routed to a partition by hashing a key extracted from each event.
 * Events with the same key always land on the same partition, preserving per-key ordering.
 * Events on different partitions are processed in parallel.
 *
 * <p>This is analogous to Kafka's partition model: per-partition total order,
 * cross-partition parallelism.
 *
 * @param <E> the type of events to process
 */
public class PartitionedDaemon<E> implements AutoCloseable {

    private final Daemon<E>[] partitions;
    private final ToIntFunction<E> keyExtractor;

    /**
     * Creates a new partitioned daemon and immediately starts one background thread per partition.
     *
     * @param partitionCount         number of partitions (and backing threads)
     * @param bufferSizePerPartition queue capacity per partition; total memory is partitionCount × bufferSizePerPartition
     * @param keyExtractor           maps an event to an integer key that determines its partition
     * @param handleEvent            handler called for each event within its partition's thread
     */
    @SuppressWarnings("unchecked")
    public PartitionedDaemon(int partitionCount, int bufferSizePerPartition,
                             ToIntFunction<E> keyExtractor,
                             BiConsumer<E, Thread> handleEvent) {
        this.partitions = new Daemon[partitionCount];
        this.keyExtractor = keyExtractor;
        for (int i = 0; i < partitionCount; i++) {
            partitions[i] = new Daemon<>(bufferSizePerPartition, handleEvent);
        }
    }

    private Daemon<E> partitionFor(E event) {
        int hash = keyExtractor.applyAsInt(event);
        int positiveHash = hash & Integer.MAX_VALUE;
        return partitions[positiveHash % partitions.length];
    }

    /**
     * Pushes an event to the partition determined by the key extractor,
     * blocking if that partition's queue is full.
     *
     * @param event the event to enqueue
     * @return {@code true} if accepted; {@code false} if the partition's daemon is closed
     * @throws InterruptedException if the calling thread is interrupted while waiting for space
     */
    public boolean pushEvent(E event) throws InterruptedException {
        return partitionFor(event).pushEvent(event);
    }

    /**
     * Pushes an event to the partition determined by the key extractor without blocking.
     *
     * @param event the event to enqueue
     * @return {@code true} if accepted; {@code false} if the queue is full or the daemon is closed
     */
    public boolean tryPushEvent(E event) {
        return partitionFor(event).tryPushEvent(event);
    }

    /**
     * Closes all partitions in parallel and waits until every partition has drained.
     *
     * @throws InterruptedException if the calling thread is interrupted while waiting for all partitions to finish
     */
    @Override
    public void close() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(partitions.length);
        for (Daemon<E> p : partitions) {
            Thread.ofVirtual().start(() -> {
                try { p.close(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { latch.countDown(); }
            });
        }
        latch.await();
    }
}
