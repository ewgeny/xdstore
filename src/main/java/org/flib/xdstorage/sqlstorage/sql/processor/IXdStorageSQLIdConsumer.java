package org.flib.xdstorage.sqlstorage.sql.processor;

import java.sql.ResultSet;

public interface IXdStorageSQLIdConsumer {

    void setId(final int rowIndex, final ResultSet rs);

}
