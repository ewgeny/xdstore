package org.flib.xdstorage.sqlstorage.resource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.XdStorageIntegerIdCounterRecord;
import org.flib.xdstorage.idgeneration.XdStorageLongIdCounterRecord;
import org.flib.xdstorage.resource.IXdStorageResourceNamingService;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.*;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStorageSQLResourceNamingService implements IXdStorageResourceNamingService {

    private static final Logger log = LogManager.getLogger(XdStorageSQLResourceNamingService.class);

    private XdStorageServicesLocator services;

    private XdStorageSQLConfiguration configuration;

    private Map<String, IXdStorageSQLDataSourceRule> rules = new ConcurrentHashMap<>();

    public XdStorageSQLResourceNamingService(final XdStorageServicesLocator provider) {
        this.services = provider;
        this.configuration = provider.getConfiguration();
    }

    public IXdStorageSQLDataSourceConfiguration getDataSourceConfig(final String dataSourceName) {
        return configuration.getDataSourceConfig(dataSourceName);
    }

    public <T> Object getChildrenResourceId(final XdStoragePolicy policy, final Object parentObjectResourceId,
                                            final Class<?> cl, final T object, final Object objectId) throws XdStorageException {
        final XdStorageSQLClassConfiguration classCfg = configuration.getClassConfig(cl);
        if (classCfg == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", cl));
        }

        if (classCfg.isParentDataSource()) {
            final XdStorageSQLResourceId parentResourceId = (XdStorageSQLResourceId) parentObjectResourceId;

            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable(classCfg.getTable());
            resourceId.setDataSource(parentResourceId.getDataSource());
            return resourceId;
        }
        return getResourceId(policy, cl, object, objectId);
    }

    @Override
    public <T> Object getResourceId(final XdStoragePolicy policy, final Class<?> cl, final T object, final Object objectId) throws XdStorageException {
        final XdStorageSQLClassConfiguration classCfg = configuration.getClassConfig(cl);
        if (classCfg == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", cl));
        }

        final IXdStorageSQLDataSourceConfiguration dataSourceCfg;
        if (classCfg.isMultiple()) {
            String defaultDataSource = null, passedDataSource = null;
            for(final XdStorageSQLRuleConfiguration ruleCfg : classCfg.getRules()) {
                if(ruleCfg.getType() == XdStorageSQLRuleType.DEFAULT) {
                    defaultDataSource = ruleCfg.getDataSource();
                } else {
                    IXdStorageSQLDataSourceRule rule = getDatasourceRule(ruleCfg.getClassName());
                    if(rule != null && rule.passed(object)) {
                        passedDataSource = ruleCfg.getDataSource();
                        break;
                    }
                }
            }
            if(passedDataSource != null) {
                dataSourceCfg = configuration.getDataSourceConfig(passedDataSource);
            } else {
                dataSourceCfg = configuration.getDataSourceConfig(defaultDataSource);
            }
        } else {
            dataSourceCfg = configuration.getDataSourceConfig(classCfg.getDataSource());
        }

        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(classCfg.getTable());
        resourceId.setDataSource(dataSourceCfg.getName());
        return resourceId;
    }

    private IXdStorageSQLDataSourceRule getDatasourceRule(final String className) {
        IXdStorageSQLDataSourceRule rule = rules.get(className);
        if(rule == null) {
            try {
                final Class<?> cl = Class.forName(className);
                rule = (IXdStorageSQLDataSourceRule) cl.newInstance();
                rule.setStorage(services.getStorage());
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                log.error("cannot initialize data source rule", e);
            }
            rules.putIfAbsent(className, rule);
            rule = rules.get(className);
        }
        return rule;
    }

    public Object getCrossDatasourceFkResourceId(final XdStorageSQLResourceId parentObjectResourceId, final XdStorageClassInfo parentObjectClassInfo,
                                                 final XdStorageSQLResourceId childObjectResourceId, final XdStorageClassInfo childObjectClassInfo) throws XdStorageException {
        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class));
        resourceId.setDataSource(childObjectResourceId.getDataSource());
        return resourceId;
    }

    public String getObjectTable(final Class<?> cl) {
        String table;
        if (cl == XdStorageTransaction.class) {
            table = "transaction";
        } else {
            final XdStorageSQLClassConfiguration config = configuration.getClassConfig(cl);
            if (config != null) {
                table = config.getTable();
            } else {
                table = cl.getSimpleName().toLowerCase();
            }
        }
        return table;
    }

    public String getPrevStateObjectTable(final Class<?> cl) {
        return getObjectTable(cl) + "_prev_state";
    }

    public String getCrossDatasourceFkTable(final Class<?> parentObjectClass, final Class<?> childObjectClass, final Class<?> fkClass) throws XdStorageException {
        final XdStorageSQLClassConfiguration parentObjectConfig = configuration.getClassConfig(parentObjectClass);
        final XdStorageSQLClassConfiguration childObjectConfig = configuration.getClassConfig(childObjectClass);
        final XdStorageSQLClassConfiguration fkConfig = configuration.getClassConfig(fkClass);
        if (fkConfig == null) {
            throw new XdStorageException(String.format("Configuration for %s was not found", XdStorageSQLCrossDatasourceFk.class));
        }

        return fkConfig.getTable() + "_" + parentObjectConfig.getTable() + "_" + childObjectConfig.getTable();
    }

    public String getPrevStateCrossDatasourceFkTable(final Class<?> parentObjectClass, final Class<?> childObjectClass, final Class<?> fkClass) throws XdStorageException {
        return getCrossDatasourceFkTable(parentObjectClass, childObjectClass, fkClass) + "_prev_state";
    }

    public String getObjectLinksTable(final Class<?> cl, final String fieldName) {
        return getObjectTable(cl) + "_" + fieldName;
    }

    public String getObjectPrevStateLinksTable(final Class<?> cl, final String fieldName) {
        return getObjectTable(cl) + "_" + fieldName + "_prev_state";
    }

    public String getInternalObjectsTable(final Class<?> cl, final String fieldName) {
        return getObjectTable(cl) + "_" + fieldName;
    }

    public String getInternalObjectsPrevStateTable(final Class<?> cl, final String fieldName) {
        return getObjectTable(cl) + "_" + fieldName + "_prev_state";
    }

    @Override
    public Object getIndexResourceId(final String indexName, final XdStoragePolicy policy, final Class<?> cl) {
        final IXdStorageSQLDataSourceConfiguration dataSourceCfg = configuration.getCentralDataSourceConfig();

        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable(getIndexTable(cl, indexName));
        resourceId.setDataSource(dataSourceCfg.getName());
        return resourceId;
    }

    public String getIndexTable(final Class<?> cl, final String indexName) {
        final String table;
        final XdStorageSQLClassConfiguration config = configuration.getClassConfig(cl);
        if (config != null) {
            table = config.getTable();
        } else {
            table = cl.getSimpleName().toLowerCase();
        }
        return table + "_" + indexName + "_idx";
    }

    public String getPrevStateIndexTable(final Class<?> cl, final String indexName) {
        final String table;
        final XdStorageSQLClassConfiguration config = configuration.getClassConfig(cl);
        if (config != null) {
            table = config.getTable();
        } else {
            table = cl.getSimpleName().toLowerCase();
        }
        return table + "_" + indexName + "_prev_state_idx";
    }

    public String getSearchIndexTable(final Class<?> cl, final String indexName) {
        final String table;
        final XdStorageSQLClassConfiguration config = configuration.getClassConfig(cl);
        if (config != null) {
            table = config.getTable();
        } else {
            table = cl.getSimpleName().toLowerCase();
        }
        return table + "_" + indexName;
    }

    public String getPrevStateSearchIndexTable(final Class<?> cl, final String indexName) {
        final String table;
        final XdStorageSQLClassConfiguration config = configuration.getClassConfig(cl);
        if (config != null) {
            table = config.getTable();
        } else {
            table = cl.getSimpleName().toLowerCase();
        }
        return table + "_" + indexName + "_prev_state";
    }

    @Override
    public Object getStructureResourceId(final Class<?> cl) {
        if (cl == XdStorageLongIdCounterRecord.class) {
            final IXdStorageSQLDataSourceConfiguration dataSourceCfg = configuration.getCentralDataSourceConfig();

            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable("counter_long");
            resourceId.setDataSource(dataSourceCfg.getName());
            return resourceId;
        }
        if (cl == XdStorageIntegerIdCounterRecord.class) {
            final IXdStorageSQLDataSourceConfiguration dataSourceCfg = configuration.getCentralDataSourceConfig();

            final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
            resourceId.setTable("counter_integer");
            resourceId.setDataSource(dataSourceCfg.getName());
            return resourceId;
        }
        throw new XdStorageRuntimeException("database resources are not needed in structure");
    }


}
