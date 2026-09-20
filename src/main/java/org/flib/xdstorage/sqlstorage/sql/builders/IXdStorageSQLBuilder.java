package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageSQLBuilder {

    String build(Object... params) throws XdStorageException;
}
