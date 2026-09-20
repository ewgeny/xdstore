package org.flib.xdstorage.sqlstorage.sql.builders.fk;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageSelectInsertCrossDatasourceFkForDeleteSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... params) throws XdStorageException {
        final String table = cast(params[0]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(table, cast(params[1]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkForDelete.name() + table;
    }

    private String build(final String table, final String prevStateTable) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(prevStateTable).append("(parent_ds, parent_id, child_id, txname)")
                .append(" SELECT parent_ds, parent_id, child_id, ?")
                .append(" FROM ").append(table)
                .append(" WHERE parent_id = ? AND child_id = ?");

        return sb.toString();
    }
}
