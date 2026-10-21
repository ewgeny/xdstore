package io.github.xdstorage.resource;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.exceptions.XdStorageException;

public interface IXdStorageResourceNamingService {

    <T> Object getResourceId(XdStoragePolicy policy, Class<?> cl, T object, Object objectId) throws XdStorageException;

    Object getIndexResourceId(String indexName, XdStoragePolicy policy, Class<?> cl);

    Object getStructureResourceId(Class<?> cl);
}
