package org.flib.xdstorage.sqlstorage.sql.builders.references;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageSelectInsertCrossDatasourceReferenceForDeleteSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... params) throws XdStorageException {
        final String table = cast(params[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(params[0]), table, cast(params[2]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceForDelete.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table, final String prevStateTable) {
        final StringBuilder sb = new StringBuilder();

        final XdStorageObjectIdField idField = clInfo.getIdField();

        sb.append("INSERT INTO ").append(prevStateTable).append("(").append(idField.getName()).append(", reference, datasource, txname)")
                .append(" SELECT ").append(idField.getName()).append(", reference, datasource, ?")
                .append(" FROM ").append(table)
                .append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
