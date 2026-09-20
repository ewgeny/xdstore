package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.IXdStorage;

public interface IXdStorageSQLDataSourceRule {

    void setStorage(IXdStorage storage);

    boolean passed(Object object);

}
