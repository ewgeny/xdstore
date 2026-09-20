package org.flib.xdstorage.sqlstorage.sql.builders.internal;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;

import java.util.Collection;


public class XdStorageSelectInternalObjectsSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[3]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), cast(values[1]), cast(values[2]), cast(values[3]), cast(values[4]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInternalObjects.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final XdStorageClassInfo internalClassInfo,
                         final Class<?> mapKeyClass, final String table, final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder();

        sb.append(" SELECT parent_id,");
        if (mapKeyClass != null) {
            sb.append("key,");
        }

        final Collection<XdStorageObjectField> fields = internalClassInfo.getFields().values();
        int count = fields.size(), i = 0;
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                sb.append(field.getName());
                if (++i < count) {
                    sb.append(',');
                }
            }
        }
        sb.append(" FROM ").append(table).append(" WHERE parent_id = ?");

        return sb.toString();
    }
}
