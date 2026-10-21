package io.github.xdstorage.structure.update;

import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.resource.XdStorageAbstractResourcesManager;
import io.github.xdstorage.transaction.XdStorageTransaction;

public interface IXdStorageStructureUpdater<T> {

    Class<?> getDataClass();

    String getUpdateName();

    XdStorageUpdateStructureResult execute(XdStorageAbstractResourcesManager resourcesManager, XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException;

    void setResult(XdStorageUpdateStructureResult result, String message, Throwable e);

    XdStorageUpdateStructureResult getResult();

    String getMessage();

    Throwable getError();
}
