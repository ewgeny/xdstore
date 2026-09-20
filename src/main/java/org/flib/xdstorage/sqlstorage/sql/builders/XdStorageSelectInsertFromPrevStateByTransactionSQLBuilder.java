package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public class XdStorageSelectInsertFromPrevStateByTransactionSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(table);
        if (sql == null) {
            sql = build(cast(values[0]), table, cast(values[2]), cast(values[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertFromPrevStateByTransaction.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo,
                         final String table, final String prevStateTable,
                         final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();

        sb.append(idField.getName()).append(',');
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                sb.append(field.getName()).append(',');
            }
        }

        sb.append("txname)");

        sb.append(" SELECT ");
        sb.append(idField.getName()).append(',');
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                sb.append(field.getName()).append(',');
            }
        }
        sb.append("NULL");
        sb.append(" FROM ").append(prevStateTable);
        sb.append(" WHERE reference IS NULL AND txname = ?");

        return sb.toString();
    }
}
