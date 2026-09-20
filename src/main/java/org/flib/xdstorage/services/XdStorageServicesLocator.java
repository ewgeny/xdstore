package org.flib.xdstorage.services;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.factories.IXdStorageReferenceProvider;
import org.flib.xdstorage.factories.XdStorageDefaultReferenceProvider;
import org.flib.xdstorage.factories.XdStorageSmartCloner;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageDefaultIdGenerator;
import org.flib.xdstorage.postgresql.XdStoragePGSQLConnectionProvider;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfigurationLoader;
import org.flib.xdstorage.sqlstorage.datasource.IXdStorageSQLDataSourceProvider;
import org.flib.xdstorage.postgresql.XdStoragePGDataSourceProvider;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.search.XdStorageSQLSearchManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.postgresql.XdStoragePGSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.processor.IXdStorageSQLConnectionProvider;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.sqlstorage.transaction.XdStorageSQLTransactionManager;
import org.flib.xdstorage.resource.IXdStorageResourceNamingService;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageResourceNamingService;
import org.flib.xdstorage.resource.XdStorageResourcesManager;
import org.flib.xdstorage.search.IXdStorageSearchManager;
import org.flib.xdstorage.search.XdStorageSearchManager;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.XdStorageDefaultIOFactory;
import org.flib.xdstorage.structure.XdStorageStructureManager;
import org.flib.xdstorage.transaction.IXdStorageTransactionManager;
import org.flib.xdstorage.transaction.XdStorageTransactionManager;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XdStorageServicesLocator {

    private IXdStorage storage;

    private ExecutorService executor;

    private IXdStorageTransactionManager transactionsManager;

    private XdStorageTriggerManager triggersManager;

    private XdStorageAbstractResourcesManager resourcesManager;

    private IXdStorageSearchManager searchManager;

    private XdStorageStructureManager structureManager;

    private IXdStorageIdGenerator idGenerator;

    private IXdStorageResourceNamingService namingService;

    private IXdStorageIOFactory ioFactory;

    private IXdStorageReferenceProvider referencesProvider;

    private IXdStorageCloner cloner;

    private XdStorageSQLConfiguration configuration;

    private IXdStorageSQLTypesHelper typesHelper;

    private IXdStorageSQLDataSourceProvider dataSourceProvider;

    private XdStorageSQLProcessor sqlProcessor;

    public void initPGConfiguration(final IXdStorage storage) {
        this.loadPGConfiguration();
        this.storage = storage;
        this.executor = Executors.newFixedThreadPool(5);
        this.typesHelper = new XdStoragePGSQLTypesHelper();
        this.dataSourceProvider = new XdStoragePGDataSourceProvider();
        this.triggersManager = new XdStorageTriggerManager();
        this.namingService = new XdStorageSQLResourceNamingService(this);
        this.idGenerator = new XdStorageDefaultIdGenerator(this);
        this.transactionsManager = new XdStorageSQLTransactionManager(this);
        this.resourcesManager = new XdStorageSQLResourcesManager(this);
        this.structureManager = new XdStorageStructureManager(this);
        this.searchManager = new XdStorageSQLSearchManager(this);
        this.referencesProvider = new XdStorageDefaultReferenceProvider(this.storage);
        this.cloner = new XdStorageSmartCloner(this.referencesProvider);
        final IXdStorageSQLConnectionProvider connectionProvider =
                new XdStoragePGSQLConnectionProvider((XdStorageSQLResourceNamingService) namingService, dataSourceProvider);
        this.sqlProcessor = new XdStorageSQLProcessor(connectionProvider);
    }

    public void initFileConfiguration(final IXdStorage storage, final String folder, final int fragmentSize) {
        this.storage = storage;
        this.executor = Executors.newFixedThreadPool(5);
        this.namingService = new XdStorageResourceNamingService(folder, "xml");
        this.idGenerator = new XdStorageDefaultIdGenerator(this);
        this.ioFactory = new XdStorageDefaultIOFactory(this, idGenerator);
        this.triggersManager = new XdStorageTriggerManager();
        this.transactionsManager = new XdStorageTransactionManager(this);
        this.resourcesManager = new XdStorageResourcesManager(this, fragmentSize);
        this.structureManager = new XdStorageStructureManager(this);
        this.searchManager = new XdStorageSearchManager(this);
        this.referencesProvider = new XdStorageDefaultReferenceProvider(this.storage);
        this.cloner = new XdStorageSmartCloner(this.referencesProvider);
    }

    public IXdStorage getStorage() {
        return storage;
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    public IXdStorageTransactionManager getTransactionsManager() {
        return transactionsManager;
    }

    public XdStorageTriggerManager getTriggersManager() {
        return triggersManager;
    }

    public XdStorageAbstractResourcesManager getResourcesManager() {
        return resourcesManager;
    }

    public IXdStorageSearchManager getSearchManager() {
        return searchManager;
    }

    public XdStorageStructureManager getStructureManager() {
        return structureManager;
    }

    public IXdStorageIdGenerator getIdGenerator() {
        return idGenerator;
    }

    public IXdStorageResourceNamingService getNamingService() {
        return namingService;
    }

    public IXdStorageIOFactory getIoFactory() {
        return ioFactory;
    }

    public XdStorageSQLConfiguration getConfiguration() {
        return configuration;
    }

    public IXdStorageSQLTypesHelper getTypesHelper() {
        return typesHelper;
    }

    public IXdStorageSQLDataSourceProvider getDataSourceProvider() {
        return dataSourceProvider;
    }

    public IXdStorageCloner getCloner() {
        return cloner;
    }

    public IXdStorageReferenceProvider getReferencesProvider() {
        return referencesProvider;
    }

    public XdStorageSQLProcessor getSqlProcessor() {
        return sqlProcessor;
    }

    private void loadPGConfiguration() {
        configuration = XdStorageSQLConfigurationLoader.load("pgcfg.xml");
    }

    public void shutdown() {
        if (this.dataSourceProvider != null) {
            this.dataSourceProvider.shutdown();
        }
        this.executor.shutdown();
    }
}
