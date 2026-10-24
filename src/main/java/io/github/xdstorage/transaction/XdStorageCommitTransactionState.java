package io.github.xdstorage.transaction;

/**
 * The phase of a two-phase commit, as tracked in a {@code XdStorageCommitTransactionStateHolder}
 * while the commit runs. The engine's commit path moves a holder through {@link #PREPARING} and
 * {@link #PREPARED} to {@link #FINISHED}; the transaction manager fires the persistence triggers
 * only when it observes {@link #FINISHED}.
 *
 * <p>Engine-internal — not part of the public API.
 */
public enum XdStorageCommitTransactionState {

    /**
     * Phase 1 has started: the changed resources are being locked and are writing their changes
     * (still invisible to other transactions). A failure here aborts the whole commit.
     */
    PREPARING,

    /**
     * Phase 1 finished: every changed resource has recorded its changes; phase 2 (cache cleanup
     * and version confirmation over all registered resources) is next.
     */
    PREPARED,

    /**
     * Declared state for a commit that was rolled back; the current engine commit flow never
     * sets it.
     */
    ROLLEDBACK,

    /**
     * Both phases finished: the transaction's changes are durable and visible to later readers.
     */
    FINISHED,

    /**
     * Declared state for a commit in progress; the current engine commit flow never sets it (it
     * distinguishes the in-commit phases with {@link #PREPARING} and {@link #PREPARED} instead).
     */
    ACTIVE;
}
