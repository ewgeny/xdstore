package org.flib.xdstorage.trigger;

import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

public interface IXdStorageTrigger<T> {

    XdStorageObjectOperationType getType();

    Class<T> getClazz();

    /**
     * If this method works with storage, then this one must start and finish
     * transaction.
     */
    void perform(T oldObject, T newObject, IXdStorageTransaction transaction);

}
