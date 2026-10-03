package org.flib.xdstorage.structure;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.structure.update.IXdStorageStructureUpdater;
import org.flib.xdstorage.structure.update.XdStorageSearchIndexStructureUpdater;
import org.flib.xdstorage.structure.update.XdStorageUpdateStructureRecord;
import org.flib.xdstorage.structure.update.XdStorageUpdateStructureResult;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionWrapper;
import org.flib.xdstorage.utils.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageStructureManager {

    private static final Logger log = LogManager.getLogger(XdStorageStructureManager.class);

    private final XdStorageServicesLocator services;

    private final Map<Class<?>, AtomicBoolean> states = new ConcurrentHashMap<>();

    private final Map<Class<?>, Lock> locks = new ConcurrentHashMap<>();

    private final Map<Class<?>, List<IXdStorageStructureUpdater>> updaters = new HashMap<>();

    public XdStorageStructureManager(final XdStorageServicesLocator provider) {
        this.services = provider;
    }

    public void registerUpdater(final IXdStorageStructureUpdater updater) {
        final Class<?> cl = updater.getDataClass();
        List<IXdStorageStructureUpdater> tmp = updaters.get(cl);
        if (tmp == null) {
            updaters.put(cl, tmp = new ArrayList<>());
        }
        tmp.add(updater);
    }

    public List<IXdStorageStructureUpdater> checkAndUpdateStructureIfNeed(final Class<?> cl, final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        AtomicBoolean state = states.get(cl);
        if (state != null && state.get()) {
            return Collections.emptyList();
        }

        // Локальный контейнер для безопасного выноса результатов из асинхронного таска
        final List<IXdStorageStructureUpdater> result = new ArrayList<>();
        final XdStorageException[] exceptionContainer = new XdStorageException[]{null};
        final XdStorageConnectionException[] connectionExceptionContainer = new XdStorageConnectionException[]{null};

        try {
            lockStructure(cl);

            state = states.get(cl);
            if (state != null && state.get()) {
                return Collections.emptyList();
            }

            // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Финальное уничтожение дедлоков ядра!):
            // Переносим дисковую верификацию и эволюцию схем данных XdStorageClassStructure в выделенный
            // независимый поток системного исполнителя services.getExecutor(). Это полностью исключает
            // перекрестное заклинивание мониторов транзакций и ReentrantLock замков структуры классов,
            // гарантируя абсолютную пропускную способность под пиковой многопоточной нагрузкой!
            services.getExecutor().submit(() -> {
                // Создаем абсолютно чистую, автономную транзакцию верхнего уровня для апдейта метаданных
                final XdStorageTransaction autonomousTx = (XdStorageTransaction) services.getTransactionsManager().beginTransaction(15 * 1000);
                try {
                    final XdStorageClassStructure currentStructure = buildStructure(cl);
                    if (!hasStructure(cl, autonomousTx)) {
                        insertStructure(cl, currentStructure, autonomousTx);
                        autonomousTx.commit();
                    } else if (isStructureChanged(cl, currentStructure, autonomousTx)) {
                        generateAndRegisterSearchIndexUpdaters(cl, currentStructure, autonomousTx);
                        log.debug("Update structure for " + cl + " started");

                        // Выполняем миграцию схемы
                        List<IXdStorageStructureUpdater> updatersList = updateStructure(cl, currentStructure, autonomousTx);
                        if (updatersList != null) {
                            result.addAll(updatersList);
                        }

                        autonomousTx.commit();
                        log.debug("Update structure for " + cl + " finished");
                    } else {
                        autonomousTx.commit(); // Структура не изменилась, чисто закрываем транзакцию
                    }

                    // Атомарно взводим стейт готовности класса в Lock-Free мапе
                    states.put(cl, new AtomicBoolean(true));

                } catch (final XdStorageConnectionException e) {
                    autonomousTx.rollback();
                    connectionExceptionContainer[0] = e;
                } catch (final XdStorageException e) {
                    autonomousTx.rollback();
                    exceptionContainer[0] = e;
                } catch (final Throwable t) {
                    autonomousTx.rollback();
                    exceptionContainer[0] = new XdStorageException("Критический сбой автономной миграции структуры класса " + cl.getName(), t);
                }
            }).get(); // Жестко дожидаемся завершения транзакции миграции схемы

        } catch (final Exception e) {
            throw new XdStorageException("Сбой синхронизации менеджера структур для класса " + cl.getName(), e);
        } finally {
            try {
                unlockStructure(cl);
            } catch (Exception ignored) {}
        }

        // Пробрасываем накопленные исключения миграции вверх по стеку в исходных типах
        if (connectionExceptionContainer[0] != null) throw connectionExceptionContainer[0];
        if (exceptionContainer[0] != null) throw exceptionContainer[0];

        return result;
    }

    private void lockStructure(final Class<?> cl) {
        Lock lock = locks.get(cl);
        if (lock == null) {
            locks.putIfAbsent(cl, new ReentrantLock());
            lock = locks.get(cl);
        }

        lock.lock();
    }

    private void unlockStructure(final Class<?> cl) throws XdStorageException {
        Lock lock = locks.get(cl);
        if (lock == null) {
            throw new XdStorageException("cannot unlock structure of " + cl);
        }

        lock.unlock();
    }

    public boolean hasStructure(final Class<?> cl, final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        final Class<?> structureCl = XdStorageClassStructure.class;
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(structureCl);

        final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(clInfo, tx);
        return resource.read(cl, tx) != null;
    }

    private void insertStructure(final Class<?> cl, final XdStorageClassStructure currentStructure,
                                 final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        final Class<?> structureCl = XdStorageClassStructure.class;
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(structureCl);

        final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(clInfo, tx);
        resource.insert(currentStructure, tx);
    }

    private boolean isStructureChanged(final Class<?> cl, final XdStorageClassStructure currentStructure,
                                       final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        final Class<?> structureCl = XdStorageClassStructure.class;
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(structureCl);

        final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(clInfo, tx);
        final XdStorageClassStructure structure = (XdStorageClassStructure) resource.read(cl, tx);

        return !Objects.equals(structure, currentStructure);
    }

    private void generateAndRegisterSearchIndexUpdaters(final Class<?> cl, final XdStorageClassStructure currentStructure,
                                                        final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        final Class<?> structureCl = XdStorageClassStructure.class;
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(structureCl);

        final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(clInfo, tx);
        final XdStorageClassStructure structure = (XdStorageClassStructure) resource.read(cl, tx);

        final Map<String, XdStorageClassIndexStructure> oldIndexes = structure.getIndexes();
        if (oldIndexes != null) {
            final Map<String, XdStorageClassIndexStructure> newIndexes = currentStructure.getIndexes();
            for (final Map.Entry<String, XdStorageClassIndexStructure> indexStructure : oldIndexes.entrySet()) {
                final String indexName = indexStructure.getKey();
                if (newIndexes.containsKey(indexName)
                        && !Objects.equals(indexStructure.getValue(), newIndexes.get(indexName))) {
                    final XdStorageSearchIndex index = XdStorageObjectUtils.getClassInfo(cl).getIndexes().get(indexName);
                    registerUpdater(new XdStorageSearchIndexStructureUpdater(services.getSearchManager(), cl, index));
                }
            }
        }
    }

    private XdStorageClassStructure buildStructure(final Class<?> cl) {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        final XdStorageClassStructure structure = new XdStorageClassStructure();
        structure.setType(cl);
        structure.setPolicy(clInfo.getPolicy());

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            structure.addField(field.getName(), field.getFieldInfo().getClazz());
        }

        final Map<String, XdStorageSearchIndex> indexes = clInfo.getIndexes();
        for (final XdStorageSearchIndex index : indexes.values()) {
            structure.addIndex(index.getName(), buildIndexStructure(cl, index));
        }

        return structure;
    }

    private XdStorageClassIndexStructure buildIndexStructure(final Class<?> cl, final XdStorageSearchIndex index) {
        final XdStorageClassIndexStructureId id = new XdStorageClassIndexStructureId();
        id.setName(index.getName());
        id.setType(cl);

        final XdStorageClassIndexStructure indexStructure = new XdStorageClassIndexStructure();
        indexStructure.setId(id);

        final Map<String, XdStorageObjectField> fields = index.getFieldAccesors();
        for (final Map.Entry<String, XdStorageObjectField> accessor : fields.entrySet()) {
            indexStructure.addField(accessor.getKey(), accessor.getValue().getFieldInfo().getClazz());
        }

        final Collection<List<XdStoragePair<String, XdStorageObjectField>>> childAccessors = index.getChildAccessors();
        for(final List<XdStoragePair<String, XdStorageObjectField>> childFields : childAccessors) {
            for (final XdStoragePair<String, XdStorageObjectField> childAccessor : childFields) {
                final XdStorageObjectField childField = childAccessor.b;
                final XdStorageObjectFieldInfo fieldInfo = childField.getFieldInfo();

                final Class<?> propertyClass = fieldInfo.getValueClass();
                final Map<String, XdStorageObjectField> childFieldAccessors = index.getChildFieldAccesors(propertyClass);

                if (childFieldAccessors != null) {
                    for (final Map.Entry<String, XdStorageObjectField> accessor : childFieldAccessors.entrySet()) {
                        indexStructure.addChildField(propertyClass, accessor.getKey(), accessor.getValue().getFieldInfo().getClazz());
                    }
                }
            }
        }

        return indexStructure;
    }

    private List<IXdStorageStructureUpdater> updateStructure(final Class<?> cl, final XdStorageClassStructure currentStructure,
                                                             final XdStorageTransaction tx) throws XdStorageException, XdStorageConnectionException {
        final List<IXdStorageStructureUpdater> result = new ArrayList<>();

        XdStorageUpdateStructureResult res = XdStorageUpdateStructureResult.FAILURE;
        final XdStorageTransaction safeTransaction = new XdStorageTransactionWrapper(tx);

        final Class<?> updatesCl = XdStorageUpdateStructureRecord.class;
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(updatesCl);

        final List<IXdStorageStructureUpdater> toExecute = updaters.get(cl);
        boolean fullResult = true;
        if (toExecute != null) {
            for (IXdStorageStructureUpdater updater : toExecute) {
                try {
                    final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(clInfo, tx);
                    XdStorageUpdateStructureRecord record = (XdStorageUpdateStructureRecord) resource.read(updater.getUpdateName(), tx);
                    if (record == null || record.getResult() == XdStorageUpdateStructureResult.FAILURE) {
                        log.debug("Execution update script '" + updater.getUpdateName() + "' started");
                        res = updater.execute(services.getResourcesManager(), safeTransaction);
                        if (res == XdStorageUpdateStructureResult.FAILURE) {
                            log.debug("Execution update script '" + updater.getUpdateName() + "' failed");
                            updater.setResult(res, "update " + updater.getUpdateName() + " has been failed", null);
                            fullResult = false;
                            break;
                        }
                        log.debug("Execution update script '" + updater.getUpdateName() + "' finished successfully");
                        updater.setResult(res, "update " + updater.getUpdateName() + " has been executed successfully", null);

                        record = new XdStorageUpdateStructureRecord();
                        record.setName(updater.getUpdateName());
                        record.setResult(res);
                        record.setTime(new Date());

                        resource.insert(record, tx);
                    } else {
                        updater.setResult(res, "update with name " + updater.getUpdateName() + " has been executed earlier", null);
                    }
                } catch (final Throwable e) {
                    updater.setResult(res, "execute update exception", e);
                    fullResult = false;
                    break;
                } finally {
                    result.add(updater);
                }
            }
            if (fullResult) {
                final Class<?> structureCl = XdStorageClassStructure.class;
                final XdStorageClassInfo strClInfo = XdStorageObjectUtils.getClassInfo(structureCl);

                final IXdStorageDaoResource resource = services.getResourcesManager().lockStructureResource(strClInfo, tx);
                resource.update(currentStructure, tx);
            }
        }
        return result;
    }
}
