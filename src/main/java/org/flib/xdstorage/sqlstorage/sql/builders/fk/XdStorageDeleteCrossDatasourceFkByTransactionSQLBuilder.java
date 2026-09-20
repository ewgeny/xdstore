package org.flib.xdstorage.sqlstorage.sql.builders.fk;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageDeleteCrossDatasourceFkByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[0]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(table);
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction.name() + table;
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("DELETE FROM ").append(table).append(" WHERE txname = ?");

        return sb.toString();
    }
}
