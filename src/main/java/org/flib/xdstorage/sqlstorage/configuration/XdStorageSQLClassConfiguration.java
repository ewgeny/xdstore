package org.flib.xdstorage.sqlstorage.configuration;

import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.List;
import java.util.Objects;

public class XdStorageSQLClassConfiguration implements Cloneable {

    private Class<?> cl;

    private boolean multiple;

    private boolean parentDataSource;

    private String dataSource;

    @XdStorageObjectId
    private String table;

    private List<XdStorageSQLRuleConfiguration> rules;

    public Class<?> getCl() {
        return cl;
    }

    public void setCl(Class<?> cl) {
        this.cl = cl;
    }

    public boolean isMultiple() {
        return multiple;
    }

    public void setMultiple(boolean multiple) {
        this.multiple = multiple;
    }

    public boolean isParentDataSource() {
        return parentDataSource;
    }

    public void setParentDataSource(boolean parentDataSource) {
        this.parentDataSource = parentDataSource;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void setRules(List<XdStorageSQLRuleConfiguration> rules) {
        this.rules = rules;
    }

    public List<XdStorageSQLRuleConfiguration> getRules() {
        return rules;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStorageSQLClassConfiguration that = (XdStorageSQLClassConfiguration) o;
        return Objects.equals(dataSource, that.dataSource) &&
                Objects.equals(table, that.table);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, table);
    }

    @Override
    public Object clone() {
        final XdStorageSQLClassConfiguration newConfig = new XdStorageSQLClassConfiguration();
        newConfig.setCl(getCl());
        newConfig.setDataSource(getDataSource());
        newConfig.setTable(getTable());
        return newConfig;
    }
}
