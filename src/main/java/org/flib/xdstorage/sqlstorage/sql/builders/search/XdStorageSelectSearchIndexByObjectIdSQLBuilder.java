package org.flib.xdstorage.sqlstorage.sql.builders.search;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageSelectSearchIndexByObjectIdSQLBuilder extends XdStorageAbstractSQLBuilder {

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
        return XdStorageSQLBuilderName.SelectSearchIndexByObjectId.name() + table + indexTableName;
    }

    public String build(final XdStorageSearchIndexTableName indexTableName, final String table, final XdStorageClassInfo clInfo) {
        final StringBuilder sb = new StringBuilder("SELECT ");

        final XdStorageObjectIdField idField = clInfo.getIdField();

        switch(indexTableName) {
            case OBJECTS_REFERENCES:
                sb.append(idField.getName()).append(", rtable ").append(", rdatasource ");
                break;
            case OBJECTS_FIELDS:
                sb.append(idField.getName()).append(", field_name ").append(", value_class_name ")
                        .append(", value_as_string ");
                break;
            case OBJECTS_CHILDREN_FIELDS:
                sb.append(idField.getName()).append(' ').append(", child_class_name ")
                        .append(", child_field_name ").append(", id_class_name ")
                        .append(", id_as_string ").append(", field_name ")
                        .append(", value_class_name ").append(", value_as_string ");
        }
        sb.append(" FROM ").append(table).append('_').append(indexTableName.getNameTableSuffix());
        sb.append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
