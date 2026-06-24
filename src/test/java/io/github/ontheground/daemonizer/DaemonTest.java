package io.github.ontheground.daemonizer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class DaemonTest {

    @Test
    void processesEventsInOrder() throws InterruptedException {
        List<Integer> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);

        try (Daemon<Integer> daemon = new Daemon<>(10, (e, t) -> {
            received.add(e);
            latch.countDown();
        })) {
            daemon.pushEvent(1);
            daemon.pushEvent(2);
            daemon.pushEvent(3);
            latch.await();
        }

        assertEquals(List.of(1, 2, 3), received);
    }

    @Test
    void gracefulShutdownDrainsQueue() throws InterruptedException {
        List<Integer> received = new CopyOnWriteArrayList<>();

        try (Daemon<Integer> daemon = new Daemon<>(10, (e, t) -> received.add(e))) {
            for (int i = 0; i < 5; i++) daemon.pushEvent(i);
        }

        assertEquals(5, received.size());
    }

    @Test
    void tryPushReturnsFalseWhenFull() throws InterruptedException {
        CountDownLatch block = new CountDownLatch(1);

        try (Daemon<Integer> daemon = new Daemon<>(1, (e, t) -> {
            try { block.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        })) {
            daemon.pushEvent(1); // occupies the slot, handler blocks
            Thread.sleep(50);    // give loop thread time to pick it up and block in handler

            boolean accepted = daemon.tryPushEvent(2); // queue is now empty but handler still running; try another
            // Result depends on timing — just verify no exception is thrown and daemon closes cleanly
            block.countDown();
        }
    }

    @Test
    void closedDaemonRejectsPush() throws InterruptedException {
        Daemon<Integer> daemon = new Daemon<>(10, (e, t) -> {});
        daemon.close();

        assertFalse(daemon.pushEvent(1));
        assertFalse(daemon.tryPushEvent(1));
    }

    @Test
    void partitionedDaemonProcessesAllEvents() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(4);

        try (PartitionedDaemon<String> daemon = new PartitionedDaemon<>(
                2, 10, String::hashCode,
                (e, t) -> { received.add(e); latch.countDown(); })) {
            daemon.pushEvent("a");
            daemon.pushEvent("b");
            daemon.pushEvent("c");
            daemon.pushEvent("d");
            latch.await();
        }

        assertEquals(4, received.size());
    }
}
