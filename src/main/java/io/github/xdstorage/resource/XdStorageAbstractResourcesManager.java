package io.github.xdstorage.resource;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.index.IXdStorageIndexDaoResource;
import io.github.xdstorage.index.IXdStorageIndexResourceObject;
import io.github.xdstorage.search.IXdStorageSearchIndexDaoResource;
import io.github.xdstorage.search.IXdStorageSearchIndexResourceObject;
import io.github.xdstorage.services.XdStorageServicesLocator;
import io.github.xdstorage.transaction.XdStorageTransaction;
import io.github.xdstorage.utils.XdStorageClassInfo;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public abstract class XdStorageAbstractResourcesManager {

    protected final XdStorageServicesLocator services;

    protected final Map<Object, IXdStorageResourceObject<?>> resources;

    /**
     * [resource_id, count_objects]
     */
    protected final Map<Class<?>, Map<Object, AtomicLong>> countObjects;

    private final Map<Object, AtomicLong> locks;

    public XdStorageAbstractResourcesManager(final XdStorageServicesLocator services) {
        this.services = services;
        this.resources = new ConcurrentHashMap<>();
        this.countObjects = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();

    }

    public IXdStorageResourceNamingService getNamingService() {
        return services.getNamingService();
    }

    public IXdStorageIndexDaoResource lockIndexResource(final XdStorageClassInfo clInfo,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageIndexResourceObject resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects || policy == XdStoragePolicy.StoreWithParentObject) {
            final Object resourceId = getNamingService().getIndexResourceId("index", policy, clInfo.getClazz());
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createIndexResource(resourceId, "index", clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.INDEX_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    protected abstract IXdStorageIndexResourceObject createIndexResource(final Object resourceId, final String indexName,
                                                                         final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockResource(final Object resourceId, final XdStorageClassInfo clInfo,
                                              final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
            @Override
            public Object getResourceId() {
                return resourceId;
            }

            @Override
            public IXdStorageResourceObject create() {
                return createResource(resourceId, clInfo);
            }

            @Override
            public int getCommitOrder() {
                return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
            }
        }, transaction);
        return resource.getDao();
    }

    public IXdStorageDaoResource lockResource(final XdStorageClassInfo clInfo, final Object id,
                                              final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStoragePolicy policy = clInfo.getPolicy();
        final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, id);
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
            @Override
            public Object getResourceId() {
                return resourceId;
            }

            @Override
            public IXdStorageResourceObject create() {
                return createResource(resourceId, clInfo);
            }

            @Override
            public int getCommitOrder() {
                return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
            }
        }, transaction);
        return resource.getDao();
    }

    protected abstract IXdStorageResourceObject<IXdStorageDaoResource> createResource(final Object resourceId,
                                                                                      final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockReferencesResource(final XdStorageClassInfo clInfo,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getIndexResourceId("references", policy, clInfo.getClazz());
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createReferencesResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.INDEX_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    protected abstract IXdStorageResourceObject<IXdStorageDaoResource> createReferencesResource(final Object resourceId,
                                                                                                final XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockClassResource(final Object object, final XdStorageClassInfo clInfo,
                                                   final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, null);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    public <T> IXdStorageDaoResource lockObjectResource(final T object,
                                                        final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getResourceId(policy, cl, object, null);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    public IXdStorageDaoResource lockObjectResource(final XdStorageClassInfo clInfo, final Object id,
                                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        IXdStorageResourceObject<IXdStorageDaoResource> resource = null;
        final XdStoragePolicy policy = clInfo.getPolicy();
        if (policy == XdStoragePolicy.StoreAsSingleObject) {
            final Object resourceId = getNamingService().getResourceId(policy, clInfo.getClazz(), null, id);
            resource = internalLockResource(new IResourceFactory() {
                @Override
                public Object getResourceId() {
                    return resourceId;
                }

                @Override
                public IXdStorageResourceObject create() {
                    return createResource(resourceId, clInfo);
                }

                @Override
                public int getCommitOrder() {
                    return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
                }
            }, transaction);
        }
        return resource.getDao();
    }

    public IXdStorageSearchIndexDaoResource lockSearchIndexResource(final XdStorageClassInfo clInfo, final String indexName,
                                                                    final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final XdStoragePolicy policy = clInfo.getPolicy();
        final Object resourceId = getNamingService().getIndexResourceId(indexName, policy, clInfo.getClazz());
        final IXdStorageSearchIndexResourceObject resource = internalLockResource(new IResourceFactory() {
            @Override
            public Object getResourceId() {
                return resourceId;
            }

            @Override
            public IXdStorageResourceObject create() {
                return createSearchIndexResource(resourceId, indexName, clInfo);
            }

            @Override
            public int getCommitOrder() {
                return XdStorageResourceCommitOrder.SEARCH_INDEX_RESOURCE_ORDER;
            }
        }, transaction);
        return resource.getDao();
    }

    protected abstract IXdStorageSearchIndexResourceObject createSearchIndexResource(final Object resourceId, String indexName, XdStorageClassInfo clInfo);

    public IXdStorageDaoResource lockStructureResource(final XdStorageClassInfo clInfo,
                                                       final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = getNamingService().getStructureResourceId(clInfo.getClazz());
        final IXdStorageResourceObject<IXdStorageDaoResource> resource = internalLockResource(new IResourceFactory() {
            @Override
            public Object getResourceId() {
                return resourceId;
            }

            @Override
            public IXdStorageResourceObject create() {
                return createResource(resourceId, clInfo);
            }

            @Override
            public int getCommitOrder() {
                return XdStorageResourceCommitOrder.DATA_RESOURCE_ORDER;
            }
        }, transaction);
        return resource.getDao();
    }

    protected interface IResourceFactory {

        Object getResourceId();

        IXdStorageResourceObject create();

        int getCommitOrder();
    }

    protected final <T extends IXdStorageResourceObject> T internalLockResource(final IResourceFactory factory,
                                                                                final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        final Object resourceId = factory.getResourceId();

        // Гарантируем атомарное создание счетчика локов без synchronized барьеров
        final AtomicLong counter = locks.computeIfAbsent(resourceId, k -> new AtomicLong(0));

        // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Полностью ликвидируем опасный synchronized(counter)!
        // Используем атомарный метод computeIfAbsent на ConcurrentHashMap для создания ресурса.
        // Это полностью устраняет перекрестное заклинивание потоков на мониторах счетчиков ресурсов
        // и критических секциях транзакций во время параллельного выполнения стресс-теста!
        IXdStorageResourceObject returnResource = resources.computeIfAbsent(resourceId, k -> {
            IXdStorageResourceObject res = factory.create();
            final Class<?> cl = res.getObjectsClass();
            countObjects.computeIfAbsent(cl, c -> new ConcurrentHashMap<>()).put(resourceId, new AtomicLong(0));
            return res;
        });

        // Безопасно регистрируем ресурс в транзакции за пределами системных блокировок менеджера
        if (!transaction.isResourceRegistered(returnResource)) {
            transaction.registerResource(returnResource, factory.getCommitOrder());
            counter.incrementAndGet();
        }

        returnResource.prepare(transaction);
        return (T) returnResource;
    }

    public void releaseResource(final IXdStorageResourceObject resource) {
        final Object resourceId = resource.getResourceId();
        final AtomicLong counter = locks.get(resourceId);

        if (counter != null) {
            // R-55: РЕСУРС БОЛЬШЕ НЕ УДАЛЯЕТСЯ ПРИ ОБНУЛЕНИИ СЧЕТЧИКА. Раньше последний
            // release выкидывал resource-объект из карты, и следующий захват создавал НОВЫЙ
            // инстанс с пустым кэшем записей (кэш у каждого ресурса СВОЙ) и prepared=false:
            // (1) файл ресурса заново парсился с диска — горячие ноды Б+Дерева перемалывали
            //     десятки тысяч parse-циклов под штормом (дисковый IO под деревными локами —
            //     топливо find retry-budget exhaustion);
            // (2) пока «осиротевший» ресурс, зарегистрированный в живой транзакции, работал
            //     параллельно с новосозданным, существовали ДВЕ реальности одного файла:
            //     записи писались в кэш сироты, а новый ресурс читал УСТАРЕВШИЙ диск — канал
            //     рассинхрона структуры валидатора и потерянных записей;
            // (3) recreate сбрасывал слотовый счетчик фрагмента в 0 (гонка выбора фрагмента).
            // Удержание ресурса делает карту ресурсов ограниченной числом когда-либо открытых
            // файлов (объекты и так живут в record-кэшах — потолок heap≈DB задокументирован
            // как предстоящий W-4). Освобождение ресурсов происходит в storage.shutdown().

            counter.decrementAndGet();
        }
    }

    public void updateCounter(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.set(resource.getObjectsCount());
    }

    public void updateCounterByObjectAdded(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.incrementAndGet();
    }

    public void updateCounterByObjectRemoved(final Class<?> cl, final IXdStorageResourceObject<?> resource) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        final AtomicLong counter = objectsCounters.get(resource.getResourceId());
        counter.decrementAndGet();
    }

    public Object getFreeResourceId(final Class<?> cl, final long fragmentSize) {
        final Map<Object, AtomicLong> objectsCounters = countObjects.get(cl);
        if (objectsCounters != null) {
            for (final Map.Entry<Object, AtomicLong> entry : objectsCounters.entrySet()) {
                final AtomicLong counter = entry.getValue();

                // НЕБЛОКИРУЮЩИЙ CAS-БАРЬЕР: Атомарно резервируем слот под будущий объект
                // прямо на этапе сканирования фрагментов. Это полностью исключает ситуацию,
                // когда 50 параллельных потоков выбирают один и тот же файл для записи,
                // предотвращая переполнение страниц и порчу дисковой базы данных!
                while (true) {
                    long currentVal = counter.get();
                    if (currentVal >= fragmentSize) {
                        break; // Текущий фрагмент заполнен полностью, идем к следующему
                    }

                    // Пытаемся занять слот через Compare-And-Swap операцию процессора
                    if (counter.compareAndSet(currentVal, currentVal + 1)) {
                        return entry.getKey(); // Успешное бронирование, отдаем ID файла!
                    }
                    // Если другой поток успел занять слот раньше - заходим на повторную CAS-проверку
                }
            }
        }
        return null;
    }
    /**
     * W-4c (docs/tasks/2.1.0/32, W4-DESIGN.md): best-effort shutdown checkpoint of every
     * journal-bearing resource — full committed-state rewrite + journal reset. After a
     * clean close every journal is empty, so a later power loss loses nothing even with
     * {@code durableWrites=false}. Never throws; returns the number of journals
     * checkpointed (busy or unprepared resources are skipped — their journals replay at
     * the next open).
     */
    public int checkpointAllJournals() {
        int checkpointed = 0;
        for (final IXdStorageResourceObject<?> resource : resources.values()) {
            if (resource instanceof IXdStorageJournalCheckpoint) {
                try {
                    if (((IXdStorageJournalCheckpoint) resource).checkpointJournalOnShutdown()) {
                        checkpointed++;
                    }
                } catch (final Throwable t) {
                    // безвредно: журнал остается — реплей при следующем открытии
                }
            }
        }
        return checkpointed;
    }
}
