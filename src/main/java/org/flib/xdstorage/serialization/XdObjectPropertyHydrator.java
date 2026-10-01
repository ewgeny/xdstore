package org.flib.xdstorage.serialization;

import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XdObjectPropertyHydrator {
    private final Map<Class<?>, Map<String, XdStorageObjectField>> cache = new ConcurrentHashMap<>();

    public Object create(final Class<?> cl) throws Exception { return cl.newInstance(); }

    public Map<String, XdStorageObjectField> getMeta(final Class<?> cl) {
        return cache.computeIfAbsent(cl, k -> XdStorageObjectUtils.getClassInfo(k).getFields());
    }

    @SuppressWarnings("unchecked")
    public Collection<Object> createCol(final String clName) throws Exception {
        return (Collection<Object>) Class.forName(clName).newInstance();
    }

    @SuppressWarnings("unchecked")
    public Map<Object, Object> createMap(final String clName) throws Exception {
        return (Map<Object, Object>) Class.forName(clName).newInstance();
    }

    public void inject(final Object target, final Map<String, XdStorageObjectField> meta, final String field, final Object val) {
        final XdStorageObjectField prop = meta.get(field);
        if (prop != null) prop.set(target, val);
    }
}
