package org.flib.xdstorage.factories.strategies;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.Date;
import java.util.Map;

public class MapClonerProcessor implements XdStorageClonerTypeProcessor {
    @Override public boolean supports(Class<?> cl, Object val) { return val instanceof Map; }

    @SuppressWarnings("unchecked")
    @Override
    public Object cloneAndWrap(Object src, IXdStorage storage, IXdStorageTransaction tx, XdStorageClonerContext context) throws Exception {
        Map<?, ?> sourceMap = (Map<?, ?>) src;
        Map<Object, Object> clone = (Map<Object, Object>) src.getClass().getDeclaredConstructor().newInstance();

        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            final Object rawKey = entry.getKey();
            Object key;
            Class<?> clKey = rawKey != null ? XdStorageObjectUtils.getEntityClass(rawKey.getClass()) : null;

            if (rawKey == null || clKey.isEnum() || XdStorageObjectUtils.isSimpleType(clKey, rawKey)) {
                key = rawKey;
            } else if (clKey == Date.class) {
                key = new Date(((Date) rawKey).getTime());
            } else {
                key = context.processCloneAndWrap(rawKey, storage, tx);
            }

            Object val = context.processCloneAndWrap(entry.getValue(), storage, tx);
            clone.put(key, val);
        }
        return clone;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object unwrapAndClone(Object src, XdStorageClonerContext context) throws Exception {
        Map<?, ?> sourceMap = (Map<?, ?>) src;
        Map<Object, Object> clone = (Map<Object, Object>) src.getClass().getDeclaredConstructor().newInstance();

        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            final Object rawKey = entry.getKey();
            Object key;
            Class<?> clKey = rawKey != null ? XdStorageObjectUtils.getEntityClass(rawKey.getClass()) : null;

            if (rawKey == null || clKey.isEnum() || XdStorageObjectUtils.isSimpleType(clKey, rawKey)) {
                key = rawKey;
            } else if (clKey == Date.class) {
                key = new Date(((Date) rawKey).getTime());
            } else {
                key = context.processUnwrapAndClone(rawKey);
            }

            Object val = context.processUnwrapAndClone(entry.getValue());
            if (key != null && val != null) {
                clone.put(key, val);
            }
        }
        return clone;
    }
}
