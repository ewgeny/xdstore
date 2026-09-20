package org.flib.xdstorage.sqlstorage.sql.builders.references;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public class XdStorageSelectInsertCrossDatasourceReferenceFromPrevStateSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(table);
        if (sql == null) {
            sql = build(cast(values[0]), table, cast(values[2]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceFromPrevState.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table, final String prevStateTable) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();

        sb.append(idField.getName()).append(", reference, datasource, txname)");

        sb.append(" SELECT ");
        sb.append(idField.getName()).append(", reference, datasource, NULL");
        sb.append(" FROM ").append(prevStateTable);
        sb.append(" WHERE reference = ? AND txname = ?");

        return sb.toString();
    }
}
