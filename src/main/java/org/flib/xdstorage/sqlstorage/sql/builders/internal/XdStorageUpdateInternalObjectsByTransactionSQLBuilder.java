package org.flib.xdstorage.sqlstorage.sql.builders.internal;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageUpdateInternalObjectsByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), table);
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.UpdateLinksByTransaction.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET txname = NULL WHERE txname = ? AND parent_id = ?");

        return sb.toString();
    }
}
