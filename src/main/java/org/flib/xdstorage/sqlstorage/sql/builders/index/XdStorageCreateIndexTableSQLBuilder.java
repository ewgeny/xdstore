package org.flib.xdstorage.sqlstorage.sql.builders.index;

import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageCreateIndexTableSQLBuilder extends XdStorageAbstractSQLBuilder {
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
        return XdStorageSQLBuilderName.CreateIndexTable.name() + table;
    }

    private String build(final Class<?> cl, final String table, final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");

        sb.append(table).append(" (");

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageObjectIdField idField = clInfo.getIdField();

        final XdStorageClassInfo clIndexInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
        final XdStorageObjectIdField idIndexField = clIndexInfo.getIdField();

        sb.append(idIndexField.getName()).append(' ').append(helper.getSQLType(idField.getFieldInfo().getValueClass()))
                .append(" PRIMARY KEY, rtable ").append(helper.getStringSQLType(String.class, 255))
                .append(", rdatasource ").append(helper.getStringSQLType(String.class, 255))
                .append(", txname ").append(helper.getStringSQLType(String.class, 54));

        sb.append(")");

        return sb.toString();
    }
}
