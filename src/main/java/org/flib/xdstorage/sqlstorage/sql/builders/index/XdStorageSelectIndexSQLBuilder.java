package org.flib.xdstorage.sqlstorage.sql.builders.index;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageSelectIndexSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(Object... values) {
        return build(cast(values[0]), cast(values[1]));
    }

    private String build(final XdStorageClassInfo clInfo, final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("SELECT * FROM ").append(table);

        return sb.toString();
    }
}
