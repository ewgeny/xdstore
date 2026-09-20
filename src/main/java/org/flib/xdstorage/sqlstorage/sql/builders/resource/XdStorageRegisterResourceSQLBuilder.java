package org.flib.xdstorage.sqlstorage.sql.builders.resource;

import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;

public class XdStorageRegisterResourceSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        return build((String) cast(values[0]));
    }

    public String build(final String table) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append("(txname, txtimestamp, txclass, txidxclass, reference, txstate) VALUES(?, ?, ?, ?, ?, ?)");

        return sb.toString();
    }
}
