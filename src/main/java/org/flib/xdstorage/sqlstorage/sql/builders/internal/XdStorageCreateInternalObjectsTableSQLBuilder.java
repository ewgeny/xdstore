package org.flib.xdstorage.sqlstorage.sql.builders.internal;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public class XdStorageCreateInternalObjectsTableSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[3]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            final XdStorageClassInfo parentClInfo = cast(values[0]);
            final XdStorageClassInfo internalClInfo = cast(values[1]);
            final Class<?> mapKeyClass = cast(values[2]);
            sql = build(parentClInfo, internalClInfo, mapKeyClass, table, cast(values[4]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.CreateInternalObjectsTable.name() + table;
    }

    private String build(final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo internalClassInfo,
                         final Class<?> mapKeyClass, final String table, final IXdStorageSQLTypesHelper helper) {
        final XdStorageObjectIdField parentClassIdField = parentClassInfo.getIdField();

        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        sb.append(table).append(" (");

        sb.append("parent_id ").append(helper.getSQLType(parentClassIdField.getFieldInfo().getValueClass())).append(',');
        if (mapKeyClass != null) {
            sb.append("key ").append(helper.getSQLType(mapKeyClass)).append(',');
        }

        final Collection<XdStorageObjectField> fields = internalClassInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                sb.append(field.getName()).append(' ').append(getType(field, helper)).append(',');
            }
        }
        sb.append("txname ").append(helper.getStringSQLType(String.class, 54));

        sb.append(")");

        return sb.toString();
    }
}
