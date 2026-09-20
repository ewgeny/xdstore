package org.flib.xdstorage.postgresql;

import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.sqlstorage.configuration.IXdStorageSQLDataSourceConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLDataSourceType;

import java.util.Objects;

public class XdStoragePGDataSourceConfiguration implements IXdStorageSQLDataSourceConfiguration {

    @XdStorageObjectId
    private String name;

    private XdStorageSQLDataSourceType type;

    private String server;

    private int port;

    private String database;

    private String user;

    private String password;

    private int initialConnections;

    private int maxConnections;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public XdStorageSQLDataSourceType getType() {
        return type;
    }

    public void setType(XdStorageSQLDataSourceType type) {
        this.type = type;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getInitialConnections() {
        return initialConnections;
    }

    public void setInitialConnections(int initialConnections) {
        this.initialConnections = initialConnections;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStoragePGDataSourceConfiguration that = (XdStoragePGDataSourceConfiguration) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public <T> T cast() {
        return (T) this;
    }
}
