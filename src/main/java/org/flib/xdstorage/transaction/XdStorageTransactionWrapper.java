package org.flib.xdstorage.transaction;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageResourceObject;

public class XdStorageTransactionWrapper extends XdStorageTransaction {

    private final XdStorageTransaction tx;

    public XdStorageTransactionWrapper(final XdStorageTransaction tx) {
        super(null, null,null,0, null);

        this.tx = tx;
    }

    @Override
    public String getTransactionId() {
        return tx.getTransactionId();
    }

    @Override
    public long getTimeout() {
        return tx.getTimeout();
    }

    @Override
    public long getTimestart() {
        return tx.getTimestart();
    }

    @Override
    public void commit() {
        throw new XdStorageRuntimeException("transaction cannot be committed in this state");
    }

    @Override
    public void rollback() {
        throw new XdStorageRuntimeException("transaction cannot be rolled back in this state");
    }

    @Override
    public boolean isResourceRegistered(final IXdStorageResourceObject resource) {
        return tx.isResourceRegistered(resource);
    }

    @Override
    public void registerResource(final IXdStorageResourceObject resource, final int commitPriority) {
        throw new XdStorageRuntimeException("transaction cannot build resource in this state");
    }

    @Override
    public boolean isTransaction(final XdStorageTransaction transaction) {
        return tx.isTransaction(transaction);
    }
}
