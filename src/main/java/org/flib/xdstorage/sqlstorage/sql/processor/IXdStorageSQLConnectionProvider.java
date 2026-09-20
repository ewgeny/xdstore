package org.flib.xdstorage.sqlstorage.sql.processor;

import org.flib.xdstorage.transaction.IXdStorageTransaction;

import java.sql.Connection;
import java.sql.SQLException;

public interface IXdStorageSQLConnectionProvider {

    Connection getNewConnection(Object resourceId);

    Connection getConnection(Object resourceId, IXdStorageTransaction transaction);

    void commitAll(IXdStorageTransaction transaction) throws SQLException;

    void rollbackAll(IXdStorageTransaction transaction);

    void safeRollbackConnection(final Connection connection);

    void safeCloseConnection(final Connection connection);
}
