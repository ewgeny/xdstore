package io.github.xdstorage.services;

import io.github.xdstorage.IXdStorage;
import io.github.xdstorage.XdStorageConfig;
import io.github.xdstorage.metrics.XdStorageMetrics;
import io.github.xdstorage.factories.IXdStorageCloner;
import io.github.xdstorage.factories.IXdStorageReferenceProvider;
import io.github.xdstorage.factories.XdStorageDefaultReferenceProvider;
import io.github.xdstorage.factories.XdStorageSmartCloner;
import io.github.xdstorage.idgeneration.IXdStorageIdGenerator;
import io.github.xdstorage.idgeneration.XdStorageDefaultIdGenerator;
import io.github.xdstorage.resource.IXdStorageResourceNamingService;
import io.github.xdstorage.resource.XdStorageAbstractResourcesManager;
import io.github.xdstorage.resource.XdStorageResourceNamingService;
import io.github.xdstorage.resource.XdStorageResourcesManager;
import io.github.xdstorage.search.IXdStorageSearchManager;
import io.github.xdstorage.search.XdStorageSearchManager;
import io.github.xdstorage.serialization.FilesFormat;
import io.github.xdstorage.serialization.IXdStorageIOFactory;
import io.github.xdstorage.serialization.XdStorageDefaultIOFactory;
import io.github.xdstorage.serialization.XdStorageIOFactory;
import io.github.xdstorage.structure.XdStorageStructureManager;
import io.github.xdstorage.transaction.IXdStorageTransactionManager;
import io.github.xdstorage.transaction.XdStorageTransactionManager;
import io.github.xdstorage.trigger.XdStorageTriggerManager;

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
    private final io.github.xdstorage.resource.XdStorageCanonicalInstanceRegistry canonicalInstances =
            new io.github.xdstorage.resource.XdStorageCanonicalInstanceRegistry();

    public io.github.xdstorage.resource.XdStorageCanonicalInstanceRegistry getCanonicalInstances() {
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
