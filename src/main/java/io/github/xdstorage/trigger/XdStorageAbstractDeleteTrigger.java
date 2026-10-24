package io.github.xdstorage.trigger;

import io.github.xdstorage.transaction.IXdStorageTransaction;

/**
 * Base class for delete triggers: implements the two-object {@code IXdStorageTrigger} contract
 * and hands subclasses only the deleted object plus the running transaction. Register a subclass
 * with {@code IXdStorage.registerTrigger(...)}; it fires inside the changing transaction, and a
 * failure rolls that transaction back.
 *
 * @param <T> the entity class the trigger observes
 */
public abstract class XdStorageAbstractDeleteTrigger<T> implements IXdStorageTrigger<T> {

    /**
     * Default constructor for subclasses.
     */
    public XdStorageAbstractDeleteTrigger() {
    }

    @Override
    public void perform(final T oldObject, final T newObject, final IXdStorageTransaction transaction) {
        perform(oldObject, transaction);
    }

    /**
     * Trigger callback, invoked when an object of the observed class is deleted.
     *
     * @param oldObject   the deleted object
     * @param transaction the transaction performing the delete (touching the storage from here
     *                    requires opening and finishing a nested transaction)
     */
    protected abstract void perform(T oldObject, IXdStorageTransaction transaction);
}
