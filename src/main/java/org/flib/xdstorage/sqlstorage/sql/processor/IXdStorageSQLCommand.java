package org.flib.xdstorage.sqlstorage.sql.processor;

import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.SQLException;

public interface IXdStorageSQLCommand extends Comparable<XdStorageAbstractSQLCommand> {

    boolean executeAlter(IXdStorageSQLConnectionProvider connectionProvider,
                      IXdStorageTransaction transaction) throws SQLException;

    void execute(IXdStorageSQLConnectionProvider connectionProvider,
                 IXdStorageTransaction transaction) throws SQLException;
}
