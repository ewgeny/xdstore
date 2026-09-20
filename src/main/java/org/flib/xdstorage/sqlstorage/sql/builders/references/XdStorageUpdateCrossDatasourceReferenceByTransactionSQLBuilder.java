package org.flib.xdstorage.sqlstorage.sql.builders.references;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageUpdateCrossDatasourceReferenceByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET txname = NULL WHERE reference = ? AND txname = ?");

        return sb.toString();
    }
}
