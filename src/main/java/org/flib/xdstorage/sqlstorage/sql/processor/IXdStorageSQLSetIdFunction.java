package org.flib.xdstorage.sqlstorage.sql.processor;

import java.sql.ResultSet;

public interface IXdStorageSQLSetIdFunction {

    void setId(Object object, ResultSet rs);
}
