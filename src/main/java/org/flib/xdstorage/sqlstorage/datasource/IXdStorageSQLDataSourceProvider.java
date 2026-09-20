package org.flib.xdstorage.sqlstorage.datasource;

import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;

import javax.sql.DataSource;

public interface IXdStorageSQLDataSourceProvider {

    DataSource newIfNotExistAndGet(IXdStorageSQLDataSourceConfiguration configuration);

    void shutdown();

}
