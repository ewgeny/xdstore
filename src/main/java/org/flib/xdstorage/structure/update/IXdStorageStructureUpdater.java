package org.flib.xdstorage.structure.update;

import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageStructureUpdater<T> {

    Class<?> getDataClass();

    String getUpdateName();

    XdStorageUpdateStructureResult execute(XdStorageAbstractResourcesManager resourcesManager, XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException;

    void setResult(XdStorageUpdateStructureResult result, String message, Throwable e);

    XdStorageUpdateStructureResult getResult();

    String getMessage();

    Throwable getError();
}
