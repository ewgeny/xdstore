package org.flib.xdstorage.sqlstorage.configuration;

public interface IXdStorageSQLDataSourceConfiguration {

    <T> T cast();

    String getName();

    String getServer();
}
