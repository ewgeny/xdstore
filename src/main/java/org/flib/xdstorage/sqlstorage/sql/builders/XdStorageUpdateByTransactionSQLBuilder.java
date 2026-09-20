package org.flib.xdstorage.sqlstorage.sql.builders;

public class XdStorageUpdateByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET txname = NULL WHERE reference IS NULL AND txname = ?");

        return sb.toString();
    }
}
