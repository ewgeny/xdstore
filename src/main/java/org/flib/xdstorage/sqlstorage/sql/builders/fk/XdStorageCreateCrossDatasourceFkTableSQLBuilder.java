package org.flib.xdstorage.sqlstorage.sql.builders.fk;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageCreateCrossDatasourceFkTableSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... params) {
        final String table = cast(params[2]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            final XdStorageClassInfo parentObjectClassInfo = cast(params[0]);
            final XdStorageClassInfo childObjectClassInfo = cast(params[1]);

            sql = build(parentObjectClassInfo, childObjectClassInfo, table, cast(params[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.CreateCrossDatasourceFkTable.name() + table;
    }

    private String build(final XdStorageClassInfo parentObjectClassInfo, final XdStorageClassInfo childObjectClassInfo, final String table,
                         final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");

        final XdStorageObjectIdField parentIdField = parentObjectClassInfo.getIdField();
        final XdStorageObjectIdField childIdField = childObjectClassInfo.getIdField();

        sb.append(table).append(" (");

        sb.append("parent_ds ").append(helper.getStringSQLType(String.class, 255))
                .append(", parent_id ").append(getType(parentIdField, helper))
                .append(", child_id ").append(getType(childIdField, helper))
                .append(", txname ").append(helper.getStringSQLType(String.class, 54));

        sb.append(")");

        return sb.toString();
    }
}
