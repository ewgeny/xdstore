package org.flib.xdstorage.sqlstorage.sql.builders.index;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageDeleteIndexByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ").append(table).append(" WHERE txname = ?");

        return sb.toString();
    }
}
