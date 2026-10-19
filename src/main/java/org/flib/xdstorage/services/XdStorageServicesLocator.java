package org.flib.xdstorage.services;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStorageConfig;
import org.flib.xdstorage.metrics.XdStorageMetrics;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.factories.IXdStorageReferenceProvider;
import org.flib.xdstorage.factories.XdStorageDefaultReferenceProvider;
import org.flib.xdstorage.factories.XdStorageSmartCloner;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.idgeneration.XdStorageDefaultIdGenerator;
import org.flib.xdstorage.resource.IXdStorageResourceNamingService;
import org.flib.xdstorage.resource.XdStorageAbstractResourcesManager;
import org.flib.xdstorage.resource.XdStorageResourceNamingService;
import org.flib.xdstorage.resource.XdStorageResourcesManager;
import org.flib.xdstorage.search.IXdStorageSearchManager;
import org.flib.xdstorage.search.XdStorageSearchManager;
import org.flib.xdstorage.serialization.FilesFormat;
import org.flib.xdstorage.serialization.IXdStorageIOFactory;
import org.flib.xdstorage.serialization.XdStorageDefaultIOFactory;
import org.flib.xdstorage.serialization.XdStorageIOFactory;
import org.flib.xdstorage.structure.XdStorageStructureManager;
import org.flib.xdstorage.transaction.IXdStorageTransactionManager;
import org.flib.xdstorage.transaction.XdStorageTransactionManager;
import org.flib.xdstorage.trigger.XdStorageTriggerManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class XdStorageServicesLocator {

    private IXdStorage storage;

    private ExecutorService executor;

    private XdStorageConfig storageConfig = XdStorageConfig.defaults();

    private XdStorageMetrics metrics;

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

    public void initFileConfiguration(final IXdStorage storage, final String folder, final int fragmentSize, FilesFormat format) {
        initFileConfiguration(storage, folder, fragmentSize, format, XdStorageConfig.defaults());
    }

    public void initFileConfiguration(final IXdStorage storage, final String folder, final int fragmentSize,
                                      final FilesFormat format, final XdStorageConfig config) {
        // конфиг обязателен ДО создания коллабораторов: ioFactory/кэши читают его при конструировании
        this.storageConfig = config == null ? XdStorageConfig.defaults() : config;
        this.metrics = new XdStorageMetrics();
        this.storage = storage;
        this.executor = Executors.newFixedThreadPool(storageConfig.getWorkerPoolSize(), workerThreadFactory(storageConfig));
        this.namingService = new XdStorageResourceNamingService(folder, format.name().toLowerCase());
        this.idGenerator = new XdStorageDefaultIdGenerator(this);
//        this.ioFactory = new XdStorageDefaultIOFactory(this, idGenerator);
        this.ioFactory = XdStorageIOFactory.instanceIOFactory(this, idGenerator, format);
        this.triggersManager = new XdStorageTriggerManager();
        this.transactionsManager = new XdStorageTransactionManager(this);
        this.resourcesManager = new XdStorageResourcesManager(this, fragmentSize);
        this.structureManager = new XdStorageStructureManager(this);
        this.searchManager = new XdStorageSearchManager(this);
        this.referencesProvider = new XdStorageDefaultReferenceProvider(this.storage);
        this.cloner = new XdStorageSmartCloner(this.referencesProvider);
    }

    /** S-4: именованные воркеры (диагностика дампов) + опционально daemon (забытый shutdown() не вешает JVM). */
    private static ThreadFactory workerThreadFactory(final XdStorageConfig config) {
        final boolean daemon = config.isDaemonWorkerThreads();
        final AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, "xdstorage-worker-" + counter.incrementAndGet());
            thread.setDaemon(daemon);
            return thread;
        };
    }

    public XdStorageConfig getStorageConfig() {
        return storageConfig;
    }

    public XdStorageMetrics getMetrics() {
        return metrics != null ? metrics : new XdStorageMetrics();
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

    public IXdStorageCloner getCloner() {
        return cloner;
    }

    /** ФАЗА 3B: реестр канонических инстансов B+Дерева (на один storage). */
    private final org.flib.xdstorage.resource.XdStorageCanonicalInstanceRegistry canonicalInstances =
            new org.flib.xdstorage.resource.XdStorageCanonicalInstanceRegistry();

    public org.flib.xdstorage.resource.XdStorageCanonicalInstanceRegistry getCanonicalInstances() {
        return canonicalInstances;
    }

    public IXdStorageReferenceProvider getReferencesProvider() {
        return referencesProvider;
    }

    public void shutdown() {
        this.executor.shutdown();
        this.canonicalInstances.clear();
    }
}
