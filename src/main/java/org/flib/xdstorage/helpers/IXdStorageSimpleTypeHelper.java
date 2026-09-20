package org.flib.xdstorage.helpers;

public interface IXdStorageSimpleTypeHelper {

    boolean isSimpleType(final Class<?> cl, final Object object);

    String simpleTypeToString(final Object object);

    Object simpleTypeFromString(final Class<?> cl, final String value);
}
