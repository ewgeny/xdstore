package org.flib.xdstorage.factories.strategies;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.Collection;

public class CollectionClonerProcessor implements XdStorageClonerTypeProcessor {
    @Override public boolean supports(Class<?> cl, Object val) { return val instanceof Collection; }

    @SuppressWarnings("unchecked")
    @Override
    public Object cloneAndWrap(Object src, IXdStorage storage, IXdStorageTransaction tx, XdStorageClonerContext context) throws Exception {
        Collection<?> sourceColl = (Collection<?>) src;
        // ИСПРАВЛЕНИЕ: Безопасный вызов конструктора Java 17+ вместо src.getClass().newInstance()
        Collection<Object> clone = (Collection<Object>) src.getClass().getDeclaredConstructor().newInstance();

        for (Object item : sourceColl) {
            clone.add(context.processCloneAndWrap(item, storage, tx));
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object unwrapAndClone(Object src, XdStorageClonerContext context) throws Exception {
        Collection<?> sourceColl = (Collection<?>) src;
        Collection<Object> clone = (Collection<Object>) src.getClass().getDeclaredConstructor().newInstance();
        for (Object item : sourceColl) {
            clone.add(context.processUnwrapAndClone(item));
        }
        return clone;
    }
}