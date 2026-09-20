package org.flib.xdstorage.sqlstorage.sql.builders.internal;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageDeleteInternalObjectsByOwnerIdSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build(cast(values[0]), cast(values[1]));
    }

    private String build(final XdStorageClassInfo clOwnerInfo, final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ").append(table).append(" WHERE parent_id = ?");

        return sb.toString();
    }
}
