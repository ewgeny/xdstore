/**
 * Operational counters (O-1): one {@link io.github.xdstorage.metrics.XdStorageMetrics}
 * instance per storage (lifecycle/IO counters) plus a JVM-wide anomaly sink
 * ({@code engineEvents()}). Monotonic advisory numbers for dashboards — never part of
 * the storage contract; export (JMX/Micrometer) is the embedder's periodic read of a
 * snapshot.
 */
package io.github.xdstorage.metrics;
