package org.flib.xdstorage.sqlstorage.sql.builders;

public class XdStorageDeleteByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ").append(table).append(" WHERE reference IS NULL AND txname = ?");

        return sb.toString();
    }
}
