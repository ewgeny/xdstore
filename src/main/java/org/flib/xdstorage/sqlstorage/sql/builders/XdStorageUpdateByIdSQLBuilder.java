package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Map;

public class XdStorageUpdateByIdSQLBuilder extends XdStorageAbstractSQLBuilder {

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
        return XdStorageSQLBuilderName.UpdateById.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table, final IXdStorageSQLTypesHelper typesProvider) {
        final StringBuilder sb = new StringBuilder();

        sb.append("UPDATE ").append(table).append(" SET ");

        final XdStorageObjectIdField idField = clInfo.getIdField();

        for (final Map.Entry<String, XdStorageObjectField> entry : clInfo.getFields().entrySet()) {
            final XdStorageObjectField field = entry.getValue();
            if (!field.isIdField() && typesProvider.isSimpleType(field)) {
                sb.append(field.getName()).append(" = ?,");
            }
        }
        sb.append("txname = ?");
        sb.append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
