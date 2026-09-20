package org.flib.xdstorage.sqlstorage.sql.builders.search;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageDeleteSearchIndexByTransactionIdSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final XdStorageSearchIndexTableName indexTableName = cast(values[0]);
        final String table = cast(values[1]);

        final String sqlId = buildSQLId(indexTableName, table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(indexTableName, table);
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final XdStorageSearchIndexTableName indexTableName, final String table) {
        return XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.name() + table + indexTableName;
    }

    public String build(final XdStorageSearchIndexTableName indexTableName, final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ").append(table).append('_').append(indexTableName.getNameTableSuffix())
                .append(" WHERE txname = ?");

        return sb.toString();
    }
}
