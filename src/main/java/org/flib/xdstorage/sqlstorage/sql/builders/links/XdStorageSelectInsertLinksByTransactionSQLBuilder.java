package org.flib.xdstorage.sqlstorage.sql.builders.links;

import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageSelectInsertLinksByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), cast(values[1]), cast(values[2]), cast(values[3]), cast(values[4]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertLinksByTransaction.name() + table;
    }

    private String build(final XdStorageClassInfo clOwnerInfo,
                         final String table, final String prevStateTable, final String fieldName,
                         final Class<?>[] classes) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        final XdStorageObjectIdField idField = clOwnerInfo.getIdField();
        sb.append(idField.getName()).append(',');

        int index = 0;
        if (classes.length > 1) {
            sb.append("key,");
            ++index;
        }

        final XdStorageClassInfo infoChild = XdStorageObjectUtils.getClassInfo(classes[index++]);
        final XdStorageObjectIdField idFieldChild = infoChild.getIdField();
        sb.append(fieldName).append('_').append(idFieldChild.getName()).append(',');

        sb.append("txname)");

        sb.append(" SELECT ");
        sb.append(idField.getName()).append(',');
        if (classes.length > 1) {
            sb.append("key,");
        }
        sb.append(fieldName).append('_').append(idFieldChild.getName()).append(",NULL");
        sb.append(" FROM ").append(prevStateTable);
        sb.append(" WHERE txname = ?");

        return sb.toString();
    }
}
