package org.flib.xdstorage.sqlstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Выделенный узел декомпозиции (Поинт В).
 * Инкапсулирует логику транзакционного роутинга шардов и выполнения пользовательских правил IXdStorageSQLDataSourceRule.
 */
public class XdStorageSQLDataSourceRouter {

    private static final Logger log = LogManager.getLogger(XdStorageSQLDataSourceRouter.class);

    private final XdStorageServicesLocator services;
    private final XdStorageSQLConfiguration configuration;
    private final Map<String, IXdStorageSQLDataSourceRule> rulesCache = new ConcurrentHashMap<>();

    public XdStorageSQLDataSourceRouter(final XdStorageServicesLocator provider) {
        this.services = provider;
        this.configuration = provider.getConfiguration();
    }

    public <T> XdStorageSQLResourceId routeResourceId(final Class<?> cl, final T object) throws XdStorageException {
        final XdStorageSQLClassConfiguration classCfg = configuration.getClassConfig(cl);
        if (classCfg == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", cl));
        }

        final IXdStorageSQLDataSourceConfiguration dataSourceCfg;
        if (classCfg.isMultiple()) {
            String defaultDataSource = null, passedDataSource = null;
            for (final XdStorageSQLRuleConfiguration ruleCfg : classCfg.getRules()) {
                if (ruleCfg.getType() == XdStorageSQLRuleType.DEFAULT) {
                    defaultDataSource = ruleCfg.getDataSource();
                } else {
                    IXdStorageSQLDataSourceRule rule = getDatasourceRule(ruleCfg.getClassName());
                    if (rule != null && rule.passed(object)) {
                        passedDataSource = ruleCfg.getDataSource();
                        break;
                    }
                }
            }
            dataSourceCfg = (passedDataSource != null) ?
                    configuration.getDataSourceConfig(passedDataSource) : configuration.getDataSourceConfig(defaultDataSource);
        } else {
            dataSourceCfg = configuration.getDataSourceConfig(classCfg.getDataSource());
        }

        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(classCfg.getTable());
        resourceId.setDataSource(dataSourceCfg.getName());
        return resourceId;
    }

    private IXdStorageSQLDataSourceRule getDatasourceRule(final String className) {
        IXdStorageSQLDataSourceRule rule = rulesCache.get(className);
        if (rule == null) {
            try {
                final Class<?> cl = Class.forName(className);
                rule = (IXdStorageSQLDataSourceRule) cl.newInstance();
                rule.setStorage(services.getStorage());
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                log.error("cannot initialize data source rule", e);
            }
            rulesCache.putIfAbsent(className, rule);
            rule = rulesCache.get(className);
        }
        return rule;
    }

    public XdStorageSQLConfiguration getConfiguration() {
        return configuration;
    }
}
