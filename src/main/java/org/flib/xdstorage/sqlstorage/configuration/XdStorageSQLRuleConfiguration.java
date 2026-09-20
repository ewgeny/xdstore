package org.flib.xdstorage.sqlstorage.configuration;

public class XdStorageSQLRuleConfiguration {

    private XdStorageSQLRuleType type;

    private String className;

    private String dataSource;

    public XdStorageSQLRuleType getType() {
        return type;
    }

    public void setType(XdStorageSQLRuleType type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }
}
