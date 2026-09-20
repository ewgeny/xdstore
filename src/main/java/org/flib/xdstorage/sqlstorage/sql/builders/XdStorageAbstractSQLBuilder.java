package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageObjectField;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class XdStorageAbstractSQLBuilder implements IXdStorageSQLBuilder {

    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    protected static String getSQL(final String id) {
        return cache.get(id);
    }

    protected static void putSQL(final String id, final String sql) {
        cache.putIfAbsent(id, sql);
    }

    protected static String getType(final XdStorageObjectField field, final IXdStorageSQLTypesHelper helper) {
        final Class<?> cl = field.getFieldInfo().getValueClass();
        String type = helper.getSQLType(cl);
        if (type == null) {
            throw new XdStorageRuntimeException("cannot convert " + cl + " to postgresql type");
        }
        return type;
    }

    protected static <T> T cast(final Object value) {
        try {
            return (T) value;
        } catch (final Throwable t) {
            throw new XdStorageRuntimeException(t);
        }
    }
}
