package org.flib.xdstorage.factories;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageWrapperFieldLocator;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

/**
 * Финальный декомпозированный оркестратор маршалинга и клонирования СУБД (Поинт В).
 * Избавлен от прямой работы с рефлексивными полями, делегируя задачи Packer и Unpacker процессорам.
 */
public class XdStorageSmartCloner implements IXdStorageCloner {

    private static final Logger log = LogManager.getLogger(XdStorageSmartCloner.class);

    private final IXdStorageReferenceProvider referencesProvider;

    public XdStorageSmartCloner(final IXdStorageReferenceProvider referencesProvider) {
        this.referencesProvider = referencesProvider;
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        referencesProvider.release(transaction);
    }

    @Override
    public Object cloneAndWrap(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        if (toClone == null) {
            return null;
        }
        try {
            return cloneAndWrapForOneLevel(toClone, storage, transaction);
        } catch (Exception e) {
            throw new XdStorageException("cannot clone and wrap object", e);
        }
    }

    private Object cloneAndWrapForOneLevel(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = toClone.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        // ФАЗА C: КАНОНИЧЕСКИЙ ИНСТАНС ДЛЯ B+ ДЕРЕВА. Инфраструктурные объекты
        // (дерево + узлы) живут в общей памяти: все транзакции обязаны получать
        // ОДИН И ТОТ ЖЕ инстанс, иначе мутации расходятся по копиям графов
        // (расщепление ключей, "does not exists" при массовом удалении).
//        if (isBTreeInfrastructure(toClone)) {
//            return XdStorageObjectUtils.getWrappedObjectOrSameObject(toClone);
//        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);
        IXdStorageSimpleWrapper result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }

        if (result.isReference__()) {
            result.lock__();
            try {
                if (result.isReference__()) {
                    // Делегируем рефлексивное заполнение полей выделенному Packer-компоненту
                    XdStorageClonerFieldPacker.packFields(result, toClone, clInfo, storage, transaction, this);
                    result.setReference__(false);
                }
            } finally {
                result.unlock__();
            }
        }
        return result;
    }

    @Override
    public Object unwrapAndClone(final Object toCloneMaybeWrapped) throws XdStorageException {
        if (toCloneMaybeWrapped == null) {
            return null;
        }

        // Извлекаем оригинальный объект из прокси-обертки
        final Object toClone = XdStorageObjectUtils.getWrappedObjectOrSameObject(toCloneMaybeWrapped);
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(toClone.getClass());

        // =========================================================================
        // АЛГОРИТМИЧЕСКИЙ БАРЬЕР СУБД (Ликвидация рекурсивных MVCC-дедлоков Дерева):
        // Служебные инфраструктурные классы СУБД (B+ Деревья и их внутренние узлы-ноды)
        // категорически ЗАПРЕЩЕНО подвергать глубокому ORM-клонированию через unwrapAndClone!
        // Переповторный рефлексивный обход их полей JavaBeans приводил к ложной перетирке версий
        // в XdStorageResourceCache. Возвращаем оригинальный инстанс объекта (Identity), полностью
        // уничтожая ошибку concurrent modification на индексах planet_idx!
        // =========================================================================
        if (cl == org.flib.xdstorage.btree.XdStorageBTree.class ||
                cl == org.flib.xdstorage.btree.XdStorageBTreeNode.class ||
                cl.getName().contains("org.flib.xdstorage.btree")) {
            log.debug("clone: btree-infrastructure instance of {} passed through live (identity preserved)", cl.getName());
            return toClone; // Возвращаем живой инстанс без рекурсивного переклонирования
        }

        try {
            return unwrapAndCloneForOneLevel(toClone);
        } catch (Exception e) {
            throw new XdStorageException("cannot clone and wrap object", e);
        }
    }

    private Object unwrapAndCloneForOneLevel(final Object toClone) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(toClone.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);

        final Object result = cl.newInstance();
        idField.set(result, id);

        // Делегируем рефлексивное извлечение полей выделенному Unpacker-компоненту
        XdStorageClonerFieldUnpacker.unpackFields(result, toClone, clInfo, this);
        return result;
    }

    @Override
    public void fillAndWrap(final Object reference, final Object object, final IXdStorage storage, final XdStorageTransaction transaction) throws XdStorageException {
        if (reference == null) throw new XdStorageException("reference cannot be null");
        if (object == null) throw new XdStorageException("clonable object cannot be null");

        if (reference instanceof IXdStorageSimpleWrapper) {
            final IXdStorageSimpleWrapper lockable = (IXdStorageSimpleWrapper) reference;
            lockable.lock__();
            try {
                fillAndWrapForOneLevel(reference, object, storage, transaction);
            } catch (Exception e) {
                throw new XdStorageException("cannot clone and wrap object", e);
            } finally {
                lockable.unlock__();
            }
        } else {
            try {
                fillAndWrapForOneLevel(reference, object, storage, transaction);
            } catch (Exception e) {
                throw new XdStorageException("cannot clone and wrap object", e);
            }
        }
    }

    private void fillAndWrapForOneLevel(final Object reference, final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        // ФАЗА C: КАНОНИЧЕСКИЙ ИНСТАНС ДЛЯ B+ ДЕРЕВА. SimpleWrapper-ссылка становится
        // пасс-тру к живому каноническому инстансу (inner подменяется через поле 'object'),
        // а не наполняется копией. Для plain-ссылок того же класса копируем все поля
        // из канона — ссылки на узлы при этом остаются живыми инстансами.
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(reference.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        if (isBTreeInfrastructure(object)) {
            // ФАЗА 3B: канонический инстанс. SimpleWrapper-ссылка ПЕРЕЗАКАНВАЕТСЯ на живой
            // канонический инстанс (inner подменяется через поле 'object') — граф дерева
            // всегда один на все транзакции; пос-транзакционные клоны устаревали и висли.
            // packFields живого канона запрещен: он подменял живые списки children/keys копиями.
            final Object canonical = XdStorageObjectUtils.getWrappedObjectOrSameObject(object);
            if (reference instanceof IXdStorageSimpleWrapper) {
                final java.lang.reflect.Field innerField =
                        XdStorageWrapperFieldLocator.getSimpleWrapperObjectField(reference.getClass());
                if (innerField != null) {
                    innerField.setAccessible(true);
                    innerField.set(reference, canonical);
                    ((IXdStorageSimpleWrapper) reference).setReference__(false);
                    return;
                }
            }
            if (canonical != null && reference.getClass() == canonical.getClass()) {
                // plain-заглушка того же класса: наполняем полями канона (списки канона не мутируются)
                XdStorageClonerFieldPacker.packFields(reference, canonical, clInfo, storage, transaction, this);
                return;
            }
        }
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            XdStorageObjectUtils.fillObject(reference, object);
        }

        // Повторно используем декомпозированный Packer-процессор
        XdStorageClonerFieldPacker.packFields(reference, object, clInfo, storage, transaction, this);
    }

    // Внутренние package-private мостики для суб-процессоров
    Object internalCloneAsReferenceAndWrapObject(final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null) {
                final Object id = idField.get(object);
                if (id == null) {
                    return XdStorageObserverService.getObservableWrapper(object);
                }
            }
            return XdStorageObjectUtils.cloneObject(object);
        }

        final Object id = idField.get(object);
        if (id == null) {
            return XdStorageObserverService.getObservableWrapper(object);
        }

        Object result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }
        return result;
    }

    Object internalUnwrapAndCloneAsReference(final Object object) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null) {
                final Object id = idField.get(object);
                if (id == null) {
                    return XdStorageObserverService.getObservableWrapper(object);
                }
            }
            return XdStorageObjectUtils.cloneObject(object);
        }

        final Object id = idField.get(object);
        if (id == null) {
            return XdStorageObserverService.getObservableWrapper(object);
        }

        final Object result = cl.newInstance();
        idField.set(result, id);
        return result;
    }

    private static boolean isBTreeInfrastructure(final Object object) {
        if (object == null) {
            return false;
        }
        final Class<?> entityClass = XdStorageObjectUtils.getEntityClass(object.getClass());
        return entityClass == org.flib.xdstorage.btree.XdStorageBTree.class
                || entityClass == org.flib.xdstorage.btree.XdStorageBTreeNode.class
                || entityClass.getName().contains("org.flib.xdstorage.btree");
    }
}
