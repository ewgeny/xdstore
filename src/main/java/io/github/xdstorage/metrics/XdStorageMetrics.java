package io.github.xdstorage.metrics;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * O-1: thread-safe counter set for operational visibility (zero allocation on the hot path:
 * {@link LongAdder} per counter name). One instance per storage (lifecycle/IO counters) plus
 * one JVM-wide anomaly sink for components that have no per-storage wiring (RW-lock timeouts,
 * retry-budget exhaustions) — see {@link #engineEvents()}.
 *
 * <p>Counters are monotonic advisory numbers for dashboards/alerts, not part of any contract;
 * the open name-keyed set lets the engine grow counters without API churn.</p>
 */
public class XdStorageMetrics {

    /** Well-known per-storage counter names. */
    /** Transactions opened on this storage. */
    public static final String TRANSACTIONS_BEGUN = "transactionsBegun";
    /** Transactions committed successfully. */
    public static final String TRANSACTIONS_COMMITTED = "transactionsCommitted";
    /** Transactions rolled back (explicitly or by timeout sweep). */
    public static final String TRANSACTIONS_ROLLED_BACK = "transactionsRolledBack";
    /** Rollback-only marks applied to transactions (wrapper accessors, search errors). */
    public static final String ROLLBACK_ONLY_MARKS = "rollbackOnlyMarks";
    /** Rollbacks executed by the lazy timeout sweeper. */
    public static final String TIMEOUT_SWEPT_ROLLBACKS = "timeoutSweptRollbacks";
    /** Gauge (not a monotonic counter): transactions currently open. */
    public static final String ACTIVE_TRANSACTIONS = "activeTransactions";
    /** Resource files written out (per commit). */
    public static final String RESOURCE_WRITE_OUTS = "resourceWriteOuts";
    /** Nanoseconds spent writing resource files out (divide by RESOURCE_WRITE_OUTS for the mean). */
    public static final String RESOURCE_WRITE_OUT_NANOS = "resourceWriteOutNanos";

    /** Well-known JVM-wide engine-anomaly counter names. */
    /** JVM-wide: lock-acquisition attempts that timed out. */
    public static final String LOCK_ACQUISITION_TIMEOUTS = "lockAcquisitionTimeouts";
    /** JVM-wide: lock waits longer than one second. */
    public static final String LOCK_WAITS_OVER_1S = "lockWaitsOver1s";
    /** JVM-wide: find-retry budgets exhausted under contention. */
    public static final String FIND_RETRY_BUDGET_EXHAUSTIONS = "findRetryBudgetExhaustions";

    private static final XdStorageMetrics ENGINE_EVENTS = new XdStorageMetrics();

    /** A fresh counter set (an embedder's custom sink; storages get theirs from the engine). */
    public XdStorageMetrics() {
    }

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    /**
     * JVM-wide anomaly sink: lock/retry events are rare-by-design signals; mixing storages is acceptable for them.
     *
     * @return the shared JVM-wide metrics instance
     */
    public static XdStorageMetrics engineEvents() {
        return ENGINE_EVENTS;
    }

    /**
     * Increments a counter by one (creating it on first use).
     *
     * @param name the counter name (a well-known constant or a custom name)
     */
    public void increment(final String name) {
        counter(name).increment();
    }

    /**
     * Decrements a counter by one (for gauges like {@link #ACTIVE_TRANSACTIONS}).
     *
     * @param name the counter name
     */
    public void decrement(final String name) {
        counter(name).decrement();
    }

    /**
     * Adds a delta to a counter (durations, batch sizes).
     *
     * @param name  the counter name
     * @param delta the amount to add
     */
    public void add(final String name, final long delta) {
        counter(name).add(delta);
    }

    /**
     * Reads a counter's current value.
     *
     * @param name the counter name
     * @return the value; 0 for an unknown name
     */
    public long get(final String name) {
        final LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    /**
     * Point-in-time snapshot of all counters, sorted by name (dashboards, log lines, tests).
     *
     * @return a fresh map of every counter name to its current value
     */
    public Map<String, Long> snapshot() {
        final Map<String, Long> result = new TreeMap<>();
        for (final Map.Entry<String, LongAdder> entry : counters.entrySet()) {
            result.put(entry.getKey(), entry.getValue().sum());
        }
        return result;
    }

    private LongAdder counter(final String name) {
        return counters.computeIfAbsent(name, k -> new LongAdder());
    }

    /** The {@link #snapshot()} map's string form. */
    @Override
    public String toString() {
        return snapshot().toString();
    }
}
