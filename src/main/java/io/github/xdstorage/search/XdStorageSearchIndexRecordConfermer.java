package io.github.xdstorage.search;

import io.github.xdstorage.utils.XdStorageClassInfo;
import io.github.xdstorage.utils.XdStorageObjectIdField;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;

public class XdStorageSearchIndexRecordConfermer {

    private Object id;

    private Map<Class<?>, Set<Object>> objectsWithoutIdentifier = new HashMap<>();

    public void registerObjectWithoutIdentifier(final Object object) {
        final Class<?> cl = object.getClass();

        Set<Object> references = objectsWithoutIdentifier.get(cl);
        if(references == null) {
            objectsWithoutIdentifier.put(cl, references = new HashSet<>());
        }

        references.add(object);
    }

    public void unregisterObjectWithoutIdentifier(final Object object) {
        final Class<?> cl = object.getClass();

        Set<Object> references = objectsWithoutIdentifier.get(cl);
        references.remove(object);

        if(references.isEmpty()) {
            objectsWithoutIdentifier.remove(cl);
        }
    }

    public boolean noObjectsWithoutIdentifier() {
        return objectsWithoutIdentifier.isEmpty();
    }

    public void setIdForRootObject(final Object id) {
        this.id = id;
    }

    public Object getIdForRootObject() {
        return id;
    }
}
