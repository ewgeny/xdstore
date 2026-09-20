package org.flib.xdstorage.sqlstorage.sql.builders.index;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageUpdateIndexByIdSQLBuilder extends XdStorageAbstractSQLBuilder {

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
        return XdStorageSQLBuilderName.UpdateIndexById.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET rtable = ?, rdatasource = ?, txname = ?");

        final XdStorageObjectIdField idField = clInfo.getIdField();

        sb.append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
