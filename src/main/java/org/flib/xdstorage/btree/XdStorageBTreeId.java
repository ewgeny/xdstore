package org.flib.xdstorage.btree;

import java.util.Objects;

public class XdStorageBTreeId {

    private Class<?> cl;

    private String name;

    public XdStorageBTreeId() {
        // do nothing
    }

    public XdStorageBTreeId(final Class<?> cl, final String name) {
        this.cl = cl;
        this.name = name;
    }

    public Class<?> getCl() {
        return cl;
    }

    public void setCl(Class<?> cl) {
        this.cl = cl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStorageBTreeId that = (XdStorageBTreeId) o;
        return Objects.equals(cl, that.cl) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cl, name);
    }

    @Override
    public String toString() {
        return cl.getSimpleName() + "-" + name;
    }
}
