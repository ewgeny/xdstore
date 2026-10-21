package io.github.xdstorage.transaction;

public enum XdStorageCommitTransactionState {

    PREPARING,
    PREPARED,
    ROLLEDBACK,
    FINISHED, ACTIVE;
}
