package org.flib.xdstorage.postgresql;

import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.datasource.IXdStorageSQLDataSourceProvider;
import org.postgresql.ds.PGPoolingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStoragePGDataSourceProvider implements IXdStorageSQLDataSourceProvider {

    private final Map<String, PGPoolingDataSource> dataSources = new ConcurrentHashMap<>();

    private final Lock lock = new ReentrantLock();

    @Override
    public DataSource newIfNotExistAndGet(final IXdStorageSQLDataSourceConfiguration configuration) {
        final XdStoragePGDataSourceConfiguration config = configuration.cast();
        final String dsName = config.getName();
        PGPoolingDataSource dataSource = dataSources.get(dsName);
        if (dataSource == null) {
            lock.lock();
            try {
                dataSource = dataSources.get(dsName);
                if (dataSource == null) {
                    dataSources.put(dsName, dataSource = createDataSource(config));
                }
            } finally {
                lock.unlock();
            }
        }
        return dataSource;
    }

    private PGPoolingDataSource createDataSource(final XdStoragePGDataSourceConfiguration config) {
        final PGPoolingDataSource dataSource = new PGPoolingDataSource();
        dataSource.setDataSourceName(config.getName());
        dataSource.setServerName(config.getServer());
        dataSource.setPortNumber(config.getPort());
        dataSource.setDatabaseName(config.getDatabase());
        dataSource.setUser(config.getUser());
        dataSource.setPassword(config.getPassword());
        dataSource.setInitialConnections(config.getInitialConnections());
        dataSource.setMaxConnections(config.getMaxConnections());
        return dataSource;
    }

    public void shutdown() {
        for(final PGPoolingDataSource dataSource : dataSources.values()) {
            dataSource.close();
        }
    }
}
