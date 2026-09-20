package org.flib.xdstorage.postgresql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.datasource.IXdStorageSQLDataSourceProvider;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.processor.IXdStorageSQLConnectionProvider;
import org.flib.xdstorage.transaction.IXdStorageTransaction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStoragePGSQLConnectionProvider implements IXdStorageSQLConnectionProvider {

    private static final Logger log = LogManager.getLogger(XdStoragePGSQLConnectionProvider.class);

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLDataSourceProvider dataSourceProvider;

    private final Map<String, Map<String, Connection>> connectionsByTransaction;

    public XdStoragePGSQLConnectionProvider(final XdStorageSQLResourceNamingService namingService,
                                            final IXdStorageSQLDataSourceProvider dataSourceProvider) {
        this.namingService = namingService;
        this.dataSourceProvider = dataSourceProvider;
        this.connectionsByTransaction = new ConcurrentHashMap<>();
    }

    @Override
    public Connection getNewConnection(Object objResourceId) {
        final XdStorageSQLResourceId resourceId = (XdStorageSQLResourceId) objResourceId;

        return openConnection(resourceId.getDataSource());
    }

    @Override
    public Connection getConnection(final Object objResourceId, final IXdStorageTransaction transaction) {
        final XdStorageSQLResourceId resourceId = (XdStorageSQLResourceId) objResourceId;

        final Map<String, Connection> connections = connectionsByTransaction
                .computeIfAbsent(transaction.getTransactionId(), transactionId -> new ConcurrentHashMap<>());

        return connections.computeIfAbsent(resourceId.getDataSource(), this::openConnection);
    }

    @Override
    public void commitAll(final IXdStorageTransaction transaction) throws SQLException {
        final Map<String, Connection> connections = connectionsByTransaction.get(transaction.getTransactionId());

        if (connections != null && !connections.isEmpty()) {
            final Iterator<Map.Entry<String, Connection>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                final Connection connection = it.next().getValue();

                connection.commit();

                safeCloseConnection(connection);

                it.remove();
            }
        }

        connectionsByTransaction.remove(transaction.getTransactionId());
    }

    @Override
    public void rollbackAll(final IXdStorageTransaction transaction) {
        final Map<String, Connection> connections = connectionsByTransaction.get(transaction.getTransactionId());

        if (connections != null && !connections.isEmpty()) {
            final Iterator<Map.Entry<String, Connection>> it = connections.entrySet().iterator();
            while (it.hasNext()) {
                final Connection connection = it.next().getValue();

                safeRollbackConnection(connection);
                safeCloseConnection(connection);

                it.remove();
            }
        }

        connectionsByTransaction.remove(transaction.getTransactionId());
    }

    private Connection openConnection(final String dataSourceName) {
        final IXdStorageSQLDataSourceConfiguration config = namingService.getDataSourceConfig(dataSourceName);
        final DataSource dataSource = dataSourceProvider.newIfNotExistAndGet(config);

        Connection connection;

        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
        } catch (final SQLException e) {
            throw new XdStorageRuntimeException("cannot connect to datasource " + config.getServer(), e);
        }

        return connection;
    }

    public void safeRollbackConnection(final Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (final SQLException e1) {
                log.error("cannot rollback transaction", e1);
            }
        }
    }

    public void safeCloseConnection(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (final SQLException e) {
                log.error("closing connection error", e);
                if (e.getNextException() != null) {
                    log.error("The next exception", e.getNextException());
                }
            }
        }
    }
}
