package org.flib.xdstorage.sqlstorage.sql.builders.index;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageSelectInsertIndexFromPrevStateByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), table, cast(values[2]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertIndexFromPrevStateByTransaction.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo,
                         final String table, final String prevStateTable) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        final XdStorageObjectIdField idField = clInfo.getIdField();
        sb.append(idField.getName()).append(", rtable, rdatasource, ");

        sb.append("txname)");

        sb.append(" SELECT ");
        sb.append(idField.getName()).append(", rtable, rdatasource, NULL");
        sb.append(" FROM ").append(prevStateTable);
        sb.append(" WHERE txname = ?");

        return sb.toString();
    }
}
