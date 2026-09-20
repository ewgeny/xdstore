package org.flib.xdstorage.sqlstorage.sql.builders.search;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageDeleteSearchIndexByObjectIdSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final XdStorageSearchIndexTableName indexTableName = cast(values[0]);
        final String table = cast(values[1]);
        final XdStorageClassInfo clInfo = cast(values[2]);

        final String sqlId = buildSQLId(indexTableName, table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(indexTableName, table, clInfo);
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final XdStorageSearchIndexTableName indexTableName, final String table) {
        return XdStorageSQLBuilderName.DeleteSearchIndexByObjectId.name() + table + indexTableName;
    }

    public String build(final XdStorageSearchIndexTableName indexTableName, final String table,
                        final XdStorageClassInfo clInfo) {
        final StringBuilder sb = new StringBuilder();

        final XdStorageObjectIdField idField = clInfo.getIdField();

        sb.append("DELETE FROM ").append(table).append('_').append(indexTableName.getNameTableSuffix())
                .append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
