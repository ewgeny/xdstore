package org.flib.xdstorage.sqlstorage.resource;

import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.Objects;

public class XdStorageSQLResourceId {

    @XdStorageObjectId
    private String table;

    private String dataSource;

    public String getTable() {
        return table;
    }

    public void setTable(final String table) {
        this.table = table;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(final String dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XdStorageSQLResourceId that = (XdStorageSQLResourceId) o;
        return Objects.equals(table, that.table) &&
                Objects.equals(dataSource, that.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(table, dataSource);
    }
}
