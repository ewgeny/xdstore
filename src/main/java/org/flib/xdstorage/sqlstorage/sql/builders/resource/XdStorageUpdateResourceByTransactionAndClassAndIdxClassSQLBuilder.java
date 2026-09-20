package org.flib.xdstorage.sqlstorage.sql.builders.resource;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageUpdateResourceByTransactionAndClassAndIdxClassSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET txstate = ? WHERE txname = ? AND txclass = ? AND txidxclass = ?");

        return sb.toString();
    }
}
