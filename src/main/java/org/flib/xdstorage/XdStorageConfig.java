package org.flib.xdstorage;

import org.flib.xdstorage.resource.XdStorageIsolationLevel;

/**
 * Immutable open-time configuration for a storage instance (SRE-OPTIMIZATION-PLAN phases S/O).
 * Every default reproduces the pre-config engine behavior exactly, so passing
 * {@link #defaults()} (or using the old provider overloads) changes nothing.
 *
 * <p>Knobs:</p>
 * <ul>
 *   <li>{@code durableWrites} — S-2: fsync the resource file before the atomic rename and
 *       fsync the parent directory after it. Default OFF: commits are process-crash-safe only.
 *       With it ON, commits are power-loss-safe at the cost of one fsync per written file.</li>
 *   <li>{@code acceptMissingEofSentinel} — S-1: when false (default), a resource file without
 *       the EOF sentinel fails loudly at open (truncation detection). Set true only to migrate
 *       a pre-sentinel (legacy) storage: the first commit rewrites every touched file with the
 *       sentinel.</li>
 *   <li>{@code daemonWorkerThreads} — S-4: worker pool threads are daemon, so a forgotten
 *       {@code shutdown()} cannot hang JVM exit. Default OFF (pre-config behavior).</li>
 *   <li>{@code registerShutdownHook} — S-4: best-effort {@code shutdown()} on JVM exit.
 *       Cannot replace {@code daemonWorkerThreads}: hooks run only after the last non-daemon
 *       thread exits, so a non-daemon idle pool blocks JVM exit before hooks ever run.</li>
 *   <li>{@code workerPoolSize} — internal worker pool size (default 15, pre-config value).</li>
 *   <li>{@code defaultTransactionTimeoutMillis} — timeout used by the no-arg
 *       {@code beginTransaction()} (default 3000, pre-config value).</li>
 *   <li>{@code isolationLevel} — MVCC isolation applied to every resource cache of the
 *       storage at open (default SNAPSHOT, pre-config value; previously unreachable via the
 *       public API).</li>
 * </ul>
 */
public final class XdStorageConfig {

    private final boolean durableWrites;
    private final boolean acceptMissingEofSentinel;
    private final boolean daemonWorkerThreads;
    private final boolean registerShutdownHook;
    private final int workerPoolSize;
    private final long defaultTransactionTimeoutMillis;
    private final XdStorageIsolationLevel isolationLevel;

    private XdStorageConfig(final Builder builder) {
        this.durableWrites = builder.durableWrites;
        this.acceptMissingEofSentinel = builder.acceptMissingEofSentinel;
        this.daemonWorkerThreads = builder.daemonWorkerThreads;
        this.registerShutdownHook = builder.registerShutdownHook;
        this.workerPoolSize = builder.workerPoolSize;
        this.defaultTransactionTimeoutMillis = builder.defaultTransactionTimeoutMillis;
        this.isolationLevel = builder.isolationLevel;
    }

    public static XdStorageConfig defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isDurableWrites() {
        return durableWrites;
    }

    public boolean isAcceptMissingEofSentinel() {
        return acceptMissingEofSentinel;
    }

    public boolean isDaemonWorkerThreads() {
        return daemonWorkerThreads;
    }

    public boolean isRegisterShutdownHook() {
        return registerShutdownHook;
    }

    public int getWorkerPoolSize() {
        return workerPoolSize;
    }

    public long getDefaultTransactionTimeoutMillis() {
        return defaultTransactionTimeoutMillis;
    }

    public XdStorageIsolationLevel getIsolationLevel() {
        return isolationLevel;
    }

    public static final class Builder {
        private boolean durableWrites = false;
        private boolean acceptMissingEofSentinel = false;
        private boolean daemonWorkerThreads = false;
        private boolean registerShutdownHook = false;
        private int workerPoolSize = 15;
        private long defaultTransactionTimeoutMillis = 3 * 1000L;
        private XdStorageIsolationLevel isolationLevel = XdStorageIsolationLevel.SNAPSHOT;

        public Builder durableWrites(final boolean value) {
            this.durableWrites = value;
            return this;
        }

        public Builder acceptMissingEofSentinel(final boolean value) {
            this.acceptMissingEofSentinel = value;
            return this;
        }

        public Builder daemonWorkerThreads(final boolean value) {
            this.daemonWorkerThreads = value;
            return this;
        }

        public Builder registerShutdownHook(final boolean value) {
            this.registerShutdownHook = value;
            return this;
        }

        public Builder workerPoolSize(final int value) {
            if (value <= 0)
                throw new IllegalArgumentException("workerPoolSize must be positive");
            this.workerPoolSize = value;
            return this;
        }

        public Builder defaultTransactionTimeoutMillis(final long value) {
            if (value <= 0)
                throw new IllegalArgumentException("defaultTransactionTimeoutMillis must be positive");
            this.defaultTransactionTimeoutMillis = value;
            return this;
        }

        public Builder isolationLevel(final XdStorageIsolationLevel value) {
            if (value == null)
                throw new IllegalArgumentException("isolationLevel cannot be null");
            this.isolationLevel = value;
            return this;
        }

        public XdStorageConfig build() {
            return new XdStorageConfig(this);
        }
    }
}
