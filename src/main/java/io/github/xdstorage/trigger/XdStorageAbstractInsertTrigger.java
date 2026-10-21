package io.github.xdstorage.trigger;

import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.transaction.XdStorageTransaction;

public abstract class XdStorageAbstractInsertTrigger<T> implements IXdStorageTrigger<T> {

    @Override
    public void perform(final T oldObject, final T newObject, final IXdStorageTransaction transaction) {
        perform(newObject, transaction);
    }

    protected abstract void perform(T object, IXdStorageTransaction transaction);
}
