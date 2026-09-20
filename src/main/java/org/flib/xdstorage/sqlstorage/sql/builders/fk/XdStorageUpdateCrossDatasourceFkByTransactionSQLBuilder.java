package org.flib.xdstorage.sqlstorage.sql.builders.fk;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageUpdateCrossDatasourceFkByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET txname = NULL WHERE txname = ?");

        return sb.toString();
    }
}
