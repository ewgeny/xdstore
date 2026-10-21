package io.github.xdstorage.trigger;

import io.github.xdstorage.resource.XdStorageObjectOperationType;
import io.github.xdstorage.transaction.IXdStorageTransaction;

public interface IXdStorageTrigger<T> {

    XdStorageObjectOperationType getType();

    Class<T> getClazz();

    /**
     * If this method works with storage, then this one must start and finish
     * transaction.
     */
    void perform(T oldObject, T newObject, IXdStorageTransaction transaction);

}
