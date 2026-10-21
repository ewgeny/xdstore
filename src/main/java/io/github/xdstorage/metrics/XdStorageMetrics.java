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
    public static final String TRANSACTIONS_BEGUN = "transactionsBegun";
    public static final String TRANSACTIONS_COMMITTED = "transactionsCommitted";
    public static final String TRANSACTIONS_ROLLED_BACK = "transactionsRolledBack";
    public static final String ROLLBACK_ONLY_MARKS = "rollbackOnlyMarks";
    public static final String TIMEOUT_SWEPT_ROLLBACKS = "timeoutSweptRollbacks";
    public static final String ACTIVE_TRANSACTIONS = "activeTransactions";
    public static final String RESOURCE_WRITE_OUTS = "resourceWriteOuts";
    public static final String RESOURCE_WRITE_OUT_NANOS = "resourceWriteOutNanos";

    /** Well-known JVM-wide engine-anomaly counter names. */
    public static final String LOCK_ACQUISITION_TIMEOUTS = "lockAcquisitionTimeouts";
    public static final String LOCK_WAITS_OVER_1S = "lockWaitsOver1s";
    public static final String FIND_RETRY_BUDGET_EXHAUSTIONS = "findRetryBudgetExhaustions";

    private static final XdStorageMetrics ENGINE_EVENTS = new XdStorageMetrics();

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    /** JVM-wide anomaly sink: lock/retry events are rare-by-design signals; mixing storages is acceptable for them. */
    public static XdStorageMetrics engineEvents() {
        return ENGINE_EVENTS;
    }

    public void increment(final String name) {
        counter(name).increment();
    }

    public void decrement(final String name) {
        counter(name).decrement();
    }

    public void add(final String name, final long delta) {
        counter(name).add(delta);
    }

    public long get(final String name) {
        final LongAdder adder = counters.get(name);
        return adder == null ? 0L : adder.sum();
    }

    /** Point-in-time snapshot of all counters, sorted by name (dashboards, log lines, tests). */
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

    @Override
    public String toString() {
        return snapshot().toString();
    }
}
