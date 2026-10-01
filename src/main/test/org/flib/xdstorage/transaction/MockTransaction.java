package org.flib.xdstorage.transaction;

import org.flib.xdstorage.IXdStorage;

public class MockTransaction implements IXdStorageTransaction {

    private String id;

    public MockTransaction(String id) {
        this.id = id;
    }

    @Override
    public IXdStorageTransactionManager<?> getTransactionManager() {
        return null;
    }

    @Override
    public IXdStorage getStorage() {
        return null;
    }

    @Override
    public String getTransactionId() {
        return id;
    }

    @Override
    public String getTransactionThreadId() {
        return null;
    }

    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public void commit() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void markRollbackOnly() {

    }

    @Override
    public boolean isRollbackOnly() {
        return false;
    }
}
