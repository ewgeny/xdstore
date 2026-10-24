package io.github.xdstorage.transaction;

/**
 * Mutable holder for the current two-phase-commit phase of one transaction. The manager creates
 * one per commit and hands it to the commit machinery, which advances it phase by phase; the
 * manager reads it back afterwards to learn how far the commit got (triggers fire only on
 * {@code FINISHED}). Not synchronized — one holder serves a single commit.
 *
 * <p>Engine-internal — not part of the public API.
 */
public class XdStorageCommitTransactionStateHolder {

    /** Creates a holder with no commit phase recorded yet. */
    public XdStorageCommitTransactionStateHolder() {
    }

    private XdStorageCommitTransactionState state;

    /**
     * The currently recorded commit phase.
     *
     * @return the phase, or {@code null} before the first {@link #setState}
     */
    public XdStorageCommitTransactionState getState() {
        return state;
    }

    /**
     * Records the commit phase.
     *
     * @param state the phase the commit has reached
     */
    public void setState(XdStorageCommitTransactionState state) {
        this.state = state;
    }
}
