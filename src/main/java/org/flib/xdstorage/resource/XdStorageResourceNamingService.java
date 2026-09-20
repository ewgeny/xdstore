package org.flib.xdstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.flib.xdstorage.utils.XdStorageStringUtils;

public class XdStorageResourceNamingService implements IXdStorageResourceNamingService {

    private static final String STRUCTURE_DIR = "structure";

    private static final String DATA_DIR = "data";

    private final String folder;

    private final String fileSuffix;

    public XdStorageResourceNamingService(final String folder, final String fileSuffix) {
        this.folder = folder;
        this.fileSuffix = fileSuffix;
    }

    @Override
    public <T> Object getResourceId(final XdStoragePolicy policy, final Class<?> cl, final T object, final Object objectId) {
        Object resourceName = null;

        final XdStorageObjectIdField idField = XdStorageObjectUtils.getClassInfo(cl).getIdField();

        if (object == null) {
            resourceName = getObjectResourceName(policy, cl, null, castObjectIdToString(objectId));
        } else if (idField != null) {
            resourceName = getObjectResourceName(policy, cl, castObjectIdToString(idField.get(object)), castObjectIdToString(objectId));
        }

        return resourceName;
    }

    @Override
    public Object getIndexResourceId(final String indexName, final XdStoragePolicy policy, final Class<?> cl) {
        return folder + "/" + DATA_DIR + "/" + cl.getSimpleName() + "/" + indexName + "." + fileSuffix;
    }

    @Override
    public Object getStructureResourceId(final Class<?> cl) {
        return folder + "/" + STRUCTURE_DIR + "/" + cl.getSimpleName() + "." + fileSuffix;
    }

    private Object getObjectResourceName(final XdStoragePolicy policy, final Class<?> cl, final String objectId, final String fragmentId) {
        Object resourceName = null;

        if (policy == XdStoragePolicy.StoreAsClassObjects) {
            if (XdStorageStringUtils.isBlank(fragmentId)) {
                resourceName = folder + "/" + DATA_DIR + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "." + fileSuffix;
            } else {
                resourceName = folder + "/" + DATA_DIR + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + fragmentId + "." + fileSuffix;
            }
        } else if (policy == XdStoragePolicy.StoreAsSingleObject) {
            if (XdStorageStringUtils.isBlank(objectId)) {
                resourceName = folder + "/" + DATA_DIR + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + fragmentId + "." + fileSuffix;
            } else {
                resourceName = folder + "/" + DATA_DIR + "/" + cl.getSimpleName() + "/" + cl.getSimpleName() + "-" + objectId + "." + fileSuffix;
            }
        }

        return resourceName;
    }

    private String castObjectIdToString(final Object objectId) {
        return objectId == null ? "" : objectId instanceof Class ? ((Class)objectId).getSimpleName() : objectId.toString();
    }
}
