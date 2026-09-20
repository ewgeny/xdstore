package org.flib.xdstorage.sqlstorage.sql.builders.search;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageCreateSearchIndexTablesSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final XdStorageSearchIndexTableName indexTableName = cast(values[0]);
        final String table = cast(values[1]);
        final XdStorageClassInfo clInfo = cast(values[2]);

        final String sqlId = buildSQLId(indexTableName, table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(indexTableName, table, clInfo, cast(values[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final XdStorageSearchIndexTableName indexTableName, final String table) {
        return XdStorageSQLBuilderName.CreateSearchIndexTable.name() + table + indexTableName;
    }

    public String build(final XdStorageSearchIndexTableName indexTableName, final String table,
                        final XdStorageClassInfo clInfo, final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");

        final XdStorageObjectIdField idField = clInfo.getIdField();

        sb.append(table).append('_').append(indexTableName.getNameTableSuffix()).append(" (");
        switch(indexTableName) {
            case OBJECTS_REFERENCES:
                sb.append(idField.getName()).append(' ').append(helper.getSQLType(idField.getFieldInfo().getValueClass()))
                        .append(" PRIMARY KEY, rtable ").append(helper.getStringSQLType(String.class, 255))
                        .append(", rdatasource ").append(helper.getStringSQLType(String.class, 255))
                        .append(", txname ").append(helper.getStringSQLType(String.class, 54));
                break;
            case OBJECTS_FIELDS:
                sb.append("rec_id ").append(helper.getPrimeryKeySQLType()).append(" PRIMARY KEY, ")
                        .append(idField.getName()).append(' ').append(helper.getSQLType(idField.getFieldInfo().getValueClass()))
                        .append(", field_name ").append(helper.getStringSQLType(String.class, 255))
                        .append(", value_class_name ").append(helper.getSQLType(Class.class))
                        .append(", value_as_string ").append(helper.getStringSQLType(String.class, 255))
                        .append(", txname ").append(helper.getStringSQLType(String.class, 54));
                break;
            case OBJECTS_CHILDREN_FIELDS:
                sb.append("rec_id ").append(helper.getPrimeryKeySQLType()).append(" PRIMARY KEY, ")
                        .append(idField.getName()).append(' ').append(helper.getSQLType(idField.getFieldInfo().getValueClass()))
                        .append(", child_class_name ").append(helper.getSQLType(Class.class))
                        .append(", child_field_name ").append(helper.getStringSQLType(String.class, 255))
                        .append(", id_class_name ").append(helper.getSQLType(Class.class))
                        .append(", id_as_string ").append(helper.getStringSQLType(String.class, 255))
                        .append(", field_name ").append(helper.getStringSQLType(String.class, 255))
                        .append(", value_class_name ").append(helper.getSQLType(Class.class))
                        .append(", value_as_string ").append(helper.getStringSQLType(String.class, 255))
                        .append(", txname ").append(helper.getStringSQLType(String.class, 54));
        }
        sb.append(")");

        return sb.toString();
    }
}
