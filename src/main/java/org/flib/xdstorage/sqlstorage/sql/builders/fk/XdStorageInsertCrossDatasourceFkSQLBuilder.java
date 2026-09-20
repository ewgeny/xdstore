package org.flib.xdstorage.sqlstorage.sql.builders.fk;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageInsertCrossDatasourceFkSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... params) throws XdStorageException {
        final String table = cast(params[0]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(table);
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.InsertCrossDatasourceFk.name() + table;
    }

    private String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append("(parent_ds, parent_id, child_id, txname) VALUES(?, ?, ?, ?)");

        return sb.toString();
    }
}
