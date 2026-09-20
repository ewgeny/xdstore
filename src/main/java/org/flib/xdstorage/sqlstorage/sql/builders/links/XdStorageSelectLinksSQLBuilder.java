package org.flib.xdstorage.sqlstorage.sql.builders.links;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

public class XdStorageSelectLinksSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build(cast(values[0]), cast(values[1]));
    }

    private String build(final XdStorageClassInfo clInfo, final String table) {
        final StringBuilder sb = new StringBuilder();

        final XdStorageObjectIdField idField = clInfo.getIdField();

        sb.append("SELECT * FROM ").append(table);
        sb.append(" WHERE ").append(idField.getName()).append(" = ?");

        return sb.toString();
    }
}
