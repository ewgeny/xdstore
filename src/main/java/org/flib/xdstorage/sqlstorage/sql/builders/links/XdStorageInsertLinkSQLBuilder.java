package org.flib.xdstorage.sqlstorage.sql.builders.links;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageInsertLinkSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), table, cast(values[2]), cast(values[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.InsertLink.name() + table;
    }

    private String build(final XdStorageClassInfo clOwnerInfo, final String table, final String fieldName,
                         final Class<?>[] classes) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        int count = 0;

        final XdStorageObjectIdField idField = clOwnerInfo.getIdField();
        sb.append(idField.getName()).append(',');
        ++count;

        int index = 0;
        if (classes.length > 1) {
            sb.append("key,");
            ++index;
            ++count;
        }

        final XdStorageClassInfo infoChild = XdStorageObjectUtils.getClassInfo(classes[index++]);
        final XdStorageObjectIdField idFieldChild = infoChild.getIdField();
        sb.append(fieldName).append('_').append(idFieldChild.getName()).append(',');
        ++count;

        sb.append("txname)");
        ++count;

        sb.append(" VALUES(");
        for (int i = 0; i < count; ++i) {
            sb.append('?');
            if (i < count - 1) {
                sb.append(",");
            }
        }
        sb.append(')');

        return sb.toString();
    }
}
