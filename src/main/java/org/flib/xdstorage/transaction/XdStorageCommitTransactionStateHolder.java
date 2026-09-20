package org.flib.xdstorage.transaction;

public class XdStorageCommitTransactionStateHolder {

    private XdStorageCommitTransactionState state;

    public XdStorageCommitTransactionState getState() {
        return state;
    }

    public void setState(XdStorageCommitTransactionState state) {
        this.state = state;
    }
}
