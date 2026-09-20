package org.flib.xdstorage.trigger;

import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;

public abstract class XdStorageAbstractInsertTrigger<T> implements IXdStorageTrigger<T> {

    @Override
    public void perform(final T oldObject, final T newObject, final IXdStorageTransaction transaction) {
        perform(newObject, transaction);
    }

    protected abstract void perform(T object, IXdStorageTransaction transaction);
}
