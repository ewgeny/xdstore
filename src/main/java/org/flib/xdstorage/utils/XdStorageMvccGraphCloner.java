package org.flib.xdstorage.utils;

import org.flib.xdstorage.code.IXdStorageUnmodifiableWrapper;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.object.XdStorageObjectConverter;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;

public final class XdStorageMvccGraphCloner {
    private XdStorageMvccGraphCloner() {}

    @SuppressWarnings("unchecked")
    public static <TObject> TObject cloneObject(final TObject object) {
        if (object == null) return null;

        final Map<Object, Object> visited = XdStorageCloningContext.get();

        // =========================================================================
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ (Разрыв зацикливаний на фазе входа фасада):
        // Опережающая проверка visited предотвращает дублирование корневых сущностей
        // при каскадных вызовах fillObject, убирая ошибку Expected: SourceData, Actual: null!
        // =========================================================================
        if (visited.containsKey(object)) {
            return (TObject) visited.get(object);
        }

        try {
            final Class<?> cl = XdStorageObjectUtils.getEntityClass(object.getClass());
            if (cl == Date.class) return (TObject) new Date(((Date) object).getTime());
            if (object instanceof XdStorageIdentifiableObject) return (TObject) XdStorageObjectConverter.convertFrom((XdStorageIdentifiableObject) object);

            if (object instanceof IXdStorageUnmodifiableWrapper) {
                final Field field = XdStorageObjectUtils.getUnmodifiableWrapperObjectField(cl);
                field.setAccessible(true);
                final Object wrappedObject = field.get(object);
                if (wrappedObject instanceof XdStorageIdentifiableObject) return (TObject) XdStorageObjectConverter.convertFrom((XdStorageIdentifiableObject) wrappedObject);
                return cloneObject((TObject) wrappedObject);
            }

            boolean isRootCall = XdStorageCloningContext.isRootCall();
            try {
                return (TObject) XdStoragePojoCloner.clonePojo(null, object, visited);
            } finally {
                if (isRootCall) XdStorageCloningContext.clear();
            }
        } catch (final Exception e) { return null; }
    }

    public static void fillFromSameTypeObject(final Object reference, final Object object) {
        final Map<Object, Object> visited = XdStorageCloningContext.get();
        boolean isRootCall = XdStorageCloningContext.isRootCall();

        // Регистрируем базовую перевязку ссылок в контекст
        visited.put(object, reference);
        try {
            XdStoragePojoCloner.clonePojo(reference, object, visited);
        } catch (Exception ignored) {
        } finally {
            if (isRootCall) XdStorageCloningContext.clear();
        }
    }
}
