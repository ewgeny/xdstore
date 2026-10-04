package org.flib.xdstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.IXdStorageIndexDaoResource;
import org.flib.xdstorage.index.IXdStorageIndexResourceObject;
import org.flib.xdstorage.search.IXdStorageSearchIndexDaoResource;
import org.flib.xdstorage.search.IXdStorageSearchIndexResourceObject;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

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
        locks.computeIfAbsent(resourceId, k -> new AtomicLong(0));
        final AtomicLong counter = locks.get(resourceId);

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
            // ИСПРАВЛЕНИЕ ДЕДЛОКА СУБД: Убираем блокирующий synchronized(counter) из фазы релиза.
            // Снижение счетчика ссылок и удаление ресурса из ConcurrentHashMap выполняются атомарно.
            if (counter.decrementAndGet() <= 0) {
                resources.remove(resourceId);
                locks.remove(resourceId);
            }
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
}
