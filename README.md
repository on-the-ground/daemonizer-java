# daemonizer

A bounded-queue daemon for sequential, backpressure-aware event processing in Java.

## Overview

`daemonizer` provides two building blocks for event-driven systems:

- **`Daemon`** — a single background thread draining a bounded queue, one event at a time in FIFO order. Producers block when the queue is full, providing natural backpressure analogous to a full Go buffered channel.
- **`PartitionedDaemon`** — N `Daemon` instances behind a key-based router. Events with the same key always go to the same partition (per-key ordering), while different partitions run in parallel (analogous to Kafka's partition model).

## Requirements

- Java 25+

## Installation

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.joohyung-park:daemonizer:0.1.1")
```

**Gradle (Groovy DSL)**
```groovy
implementation 'io.github.joohyung-park:daemonizer:0.1.1'
```

**Maven**
```xml
<dependency>
    <groupId>io.github.joohyung-park</groupId>
    <artifactId>daemonizer</artifactId>
    <version>0.1.1</version>
</dependency>
```

## Usage

### Daemon

```java
try (Daemon<String> daemon = new Daemon<>(100, (event, thread) -> {
    System.out.println("Processing: " + event);
})) {
    daemon.pushEvent("hello");   // blocks if queue is full
    daemon.tryPushEvent("world"); // returns false if queue is full, never blocks
} // close() drains remaining events before returning
```

### PartitionedDaemon

```java
try (PartitionedDaemon<Order> daemon = new PartitionedDaemon<>(
        4,                  // 4 partitions → 4 threads
        100,                // buffer size per partition
        order -> order.userId().hashCode(), // same userId → same partition → ordered
        (order, thread) -> processOrder(order)
)) {
    daemon.pushEvent(order1);
    daemon.pushEvent(order2);
} // waits for all partitions to drain
```

### Backpressure

`pushEvent` blocks the calling thread when the queue is full. This is intentional: it lets the consumer set the pace rather than allowing an unbounded accumulation of events in memory. Use `tryPushEvent` when dropping events is preferable to blocking.

## License

[MIT](LICENSE)
