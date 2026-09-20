package org.flib.xdstorage.sqlstorage.sql.builders.internal;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;

import java.util.Collection;

public class XdStorageSelectInsertInternalObjectsByOwnerIdSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[3]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), cast(values[1]), cast(values[2]), table, cast(values[4]), cast(values[5]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertInternalObjectsByOwnerId.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final XdStorageClassInfo internalClassInfo, final Class<?> mapKeyClass,
                         final String table, final String prevStateTable, final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(prevStateTable).append('(');

        sb.append("parent_id,");
        if (mapKeyClass != null) {
            sb.append("key,");
        }

        final Collection<XdStorageObjectField> fields = internalClassInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                sb.append(field.getName()).append(',');
            }
        }
        sb.append("txname) ");

        sb.append(" SELECT parent_id,");
        if (mapKeyClass != null) {
            sb.append("key,");
        }

        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                sb.append(field.getName()).append(',');
            }
        }
        sb.append("? FROM ").append(table).append(" WHERE parent_id = ?");

        return sb.toString();
    }
}
