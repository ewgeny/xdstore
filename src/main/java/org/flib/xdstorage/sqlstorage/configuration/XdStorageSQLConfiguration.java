package org.flib.xdstorage.sqlstorage.configuration;

import org.flib.xdstorage.postgresql.XdStoragePGDataSourceConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageSQLConfiguration {

    private final Map<String, XdStoragePGDataSourceConfiguration> dataSourceConfigs = new ConcurrentHashMap<>();

    private final Map<Class<?>, XdStorageSQLClassConfiguration> classesConfigs = new ConcurrentHashMap<>();

    public void addDataSourceConfig(final XdStoragePGDataSourceConfiguration config) {
        dataSourceConfigs.put(config.getName(), config);
    }

    public XdStoragePGDataSourceConfiguration getDataSourceConfig(final String name) {
        return dataSourceConfigs.get(name);
    }

    public XdStoragePGDataSourceConfiguration getCentralDataSourceConfig() {
        for (final XdStoragePGDataSourceConfiguration dataSourceConfig : dataSourceConfigs.values()) {
            if (dataSourceConfig.getType() == XdStorageSQLDataSourceType.CENTRAL) {
                return dataSourceConfig;
            }
        }
        return null;
    }

    public void addClassConfig(final XdStorageSQLClassConfiguration config) {
        classesConfigs.put(config.getCl(), config);
    }

    public XdStorageSQLClassConfiguration getClassConfig(final Class<?> cl) {
        return classesConfigs.get(cl);
    }
}
