package org.flib.xdstorage.sqlstorage.sql.builders.search;

public enum XdStorageSearchIndexTableName {

    OBJECTS_REFERENCES("ref_sidx"),

    OBJECTS_FIELDS("fields_sidx"),

    OBJECTS_CHILDREN_FIELDS("children_fields_sidx");

    private String nameTableSuffix;

    XdStorageSearchIndexTableName(String nameTableSuffix) {
        this.nameTableSuffix = nameTableSuffix;
    }

    public String getNameTableSuffix() {
        return nameTableSuffix;
    }
}
