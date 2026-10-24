package io.github.xdstorage.transaction;

import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.resource.IXdStorageResourceObject;

/**
 * A read-mostly view over another transaction: identity, timeout, start time and resource
 * registration checks delegate to the wrapped transaction, while commit, rollback and resource
 * registration are refused with {@code XdStorageRuntimeException}. Used to hand helper code
 * (e.g. structure updaters) the caller's transaction without letting it finish the transaction
 * or drag new resources into it.
 *
 * <p>Thread-safety: the wrapper itself holds only the delegate reference; its answers are as
 * thread-safe as the wrapped transaction.
 *
 * <p>Engine-internal — not part of the public API.
 */
public class XdStorageTransactionWrapper extends XdStorageTransaction {

    private final XdStorageTransaction tx;

    /**
     * Wraps the given transaction. The wrapper's own engine-side bookkeeping stays empty —
     * every meaningful call is forwarded.
     *
     * @param tx the transaction to delegate to
     */
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
