package org.flib.xdstorage.transaction;

public enum XdStorageCommitTransactionState {

    PREPARING,
    PREPARED,
    ROLLEDBACK,
    FINISHED, ACTIVE;
}
