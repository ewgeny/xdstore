package org.flib.xdstorage.resource;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageException;

public interface IXdStorageResourceNamingService {

    <T> Object getResourceId(XdStoragePolicy policy, Class<?> cl, T object, Object objectId) throws XdStorageException;

    Object getIndexResourceId(String indexName, XdStoragePolicy policy, Class<?> cl);

    Object getStructureResourceId(Class<?> cl);
}
