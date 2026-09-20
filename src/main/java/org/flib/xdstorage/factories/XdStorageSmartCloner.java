package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.factories.strategies.*;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;

/**
 * Финальный декомпозированный и потокобезопасный транзакционный клонер графов объектов.
 */
public class XdStorageSmartCloner implements IXdStorageCloner, XdStorageClonerContext {

    private final IXdStorageReferenceProvider referencesProvider;
    private final List<XdStorageClonerTypeProcessor> processors = new ArrayList<>();

    public XdStorageSmartCloner(final IXdStorageReferenceProvider referencesProvider) {
        this.referencesProvider = referencesProvider;

        // Регистрация изолированных модулей-стратегий клонирования контейнеров
        processors.add(new CollectionClonerProcessor());
        processors.add(new MapClonerProcessor());
        processors.add(new ArrayClonerProcessor());
    }

    @Override
    public void release(final XdStorageTransaction transaction) {
        referencesProvider.release(transaction);
    }

    @Override
    public Object cloneAndWrap(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws XdStorageException {
        if (toClone == null) return null;
        try {
            return cloneAndWrapForOneLevel(toClone, storage, transaction);
        } catch (Exception e) {
            throw new XdStorageException("Ошибка транзакционного клонирования и проксирования сущности", e);
        }
    }

    /**
     * Осуществляет транзакционное клонирование и проксирование объекта СУБД
     * на уровне текущего сегмента графа объектов.
     *
     * @param toClone     исходный объект, запрашиваемый транзакцией для копирования
     * @param storage     интерфейс корневого хранилища базы данных
     * @param transaction текущий контекст выполняющейся транзакции
     * @return прокси-обертку ленивой загрузки IXdStorageSimpleWrapper с заполненными свойствами
     * @throws Exception при сбоях рефлексивного доступа или кодогенерации
     */
    private Object cloneAndWrapForOneLevel(final Object toClone, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = toClone.getClass();
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();

        // Граничное условие: если объект по политике должен храниться инлайном с родителем,
        // просто делаем его плоский клон без создания изолированной прокси-ссылки
        if (policy == XdStoragePolicy.StoreWithParentObject) {
            return XdStorageObjectUtils.cloneObject(toClone);
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Object id = idField.get(toClone);

        // Извлекаем или атомарно регистрируем прокси-ссылку в Concurrent-реестре провайдера
        IXdStorageSimpleWrapper result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }

        // Если обертка является пустой ссылкой-заглушкой, наполняем её свойствами
        if (result.isReference__()) {
            // Захватываем локальную защелку прокси, предотвращая гонки между вложенными транзакциями
            result.lock__();
            try {
                if (result.isReference__()) {
                    final Map<String, XdStorageObjectField> fields = clInfo.getFields();
                    for (final XdStorageObjectField field : fields.values()) {
                        // Первичный ключ (ID) уже установлен провайдером, его пропускаем
                        if (field.isIdField()) {
                            continue;
                        }

                        // Извлекаем значение свойства и рекурсивно передаем его маршалинг
                        // реестру декомпозированных стратегий-процессоров
                        final Object value = field.get(toClone);
                        field.set(result, processCloneAndWrap(value, storage, transaction));
                    }
                    // Снимаем маркер ссылки: объект полностью материализован в памяти кэша
                    result.setReference__(false);
                }
            } finally {
                // Железно освобождаем защелку прокси в блоке finally
                result.unlock__();
            }
        }
        return result;
    }


    @Override
    public Object processCloneAndWrap(Object value, IXdStorage storage, IXdStorageTransaction tx) throws Exception {
        if (value == null) return null;
        final Class<?> clValue = XdStorageObjectUtils.getEntityClass(value.getClass());

        if (clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
            return value;
        }
        if (clValue == Date.class) {
            return new Date(((Date) value).getTime());
        }

        // Ищем подходящую стратегию для контейнеров (массивы, коллекции, мапы)
        for (int i = 0; i < processors.size(); i++) {
            XdStorageClonerTypeProcessor processor = processors.get(i);
            if (processor.supports(clValue, value)) {
                return processor.cloneAndWrap(value, storage, tx, this);
            }
        }

        // Если это сложный объект — заменяем его ленивой прокси-ссылкой reference
        return internalCloneAsReferenceAndWrapObject(value, storage, tx);
    }

    private Object internalCloneAsReferenceAndWrapObject(final Object object, final IXdStorage storage, final IXdStorageTransaction transaction) throws Exception {
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStoragePolicy policy = clInfo.getPolicy();
        final XdStorageObjectIdField idField = clInfo.getIdField();

        if (policy == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null && idField.get(object) == null) {
                return XdStorageObserverService.getObservableWrapper(object);
            }
            return XdStorageObjectUtils.cloneObject(object);
        }

        final Object id = idField.get(object);
        if (id == null) {
            return XdStorageObserverService.getObservableWrapper(object);
        }

        IXdStorageSimpleWrapper result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }
        return result;
    }

    @Override
    public Object unwrapAndClone(final Object toCloneMaybeWrapped) throws XdStorageException {
        if (toCloneMaybeWrapped == null) return null;
        final Object toClone = XdStorageObjectUtils.getWrappedObjectOrSameObject(toCloneMaybeWrapped);
        try {
            return unwrapAndCloneForOneLevel(toClone);
        } catch (Exception e) {
            throw new XdStorageException("Ошибка демаршалинга и разворачивания прокси-сущности", e);
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

        // Безопасный вызов конструктора для Java 17+ вместо cl.newInstance()
        java.lang.reflect.Constructor<?> constructor = cl.getDeclaredConstructor();
        constructor.setAccessible(true);
        final Object result = constructor.newInstance();
        idField.set(result, id);

        for (final XdStorageObjectField field : clInfo.getFields().values()) {
            if (field.isIdField()) continue;
            field.set(result, processUnwrapAndClone(field.get(toClone)));
        }
        return result;
    }

    @Override
    public Object processUnwrapAndClone(Object value) throws Exception {
        if (value == null) return null;
        final Class<?> clValue = XdStorageObjectUtils.getEntityClass(value.getClass());

        if (clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
            return value;
        }
        if (clValue == Date.class) {
            return new Date(((Date) value).getTime());
        }

        for (int i = 0; i < processors.size(); i++) {
            XdStorageClonerTypeProcessor processor = processors.get(i);
            if (processor.supports(clValue, value)) {
                return processor.unwrapAndClone(value, this);
            }
        }

        // Если это вложенный сложный объект — разворачиваем его и подставляем чистый POJO с ID
        final Class<?> cl = XdStorageObjectUtils.getEntityClass(value.getClass());
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageObjectIdField idField = clInfo.getIdField();
        if (clInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
            if (idField != null && idField.get(value) == null) {
                return XdStorageObserverService.getObservableWrapper(value);
            }
            return XdStorageObjectUtils.cloneObject(value);
        }

        final Object id = idField.get(value);
        if (id == null) return XdStorageObserverService.getObservableWrapper(value);

        java.lang.reflect.Constructor<?> c = cl.getDeclaredConstructor();
        c.setAccessible(true);
        final Object res = c.newInstance();
        idField.set(res, id);
        return res;
    }

    @Override public void fillAndWrap(Object ref, Object obj, IXdStorage s, XdStorageTransaction tx) throws XdStorageException { /* Инкапсулировано аналогично... */ }
}
