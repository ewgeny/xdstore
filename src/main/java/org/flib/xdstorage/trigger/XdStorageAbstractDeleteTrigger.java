package org.flib.xdstorage.trigger;

import org.flib.xdstorage.transaction.IXdStorageTransaction;

public abstract class XdStorageAbstractDeleteTrigger<T> implements IXdStorageTrigger<T> {

    @Override
    public void perform(final T oldObject, final T newObject, final IXdStorageTransaction transaction) {
        perform(oldObject, transaction);
    }

    protected abstract void perform(T oldObject, IXdStorageTransaction transaction);
}
