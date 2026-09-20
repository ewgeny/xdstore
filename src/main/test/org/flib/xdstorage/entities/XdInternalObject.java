package org.flib.xdstorage.entities;

import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy
public class XdInternalObject {

    private String name;

    private Integer value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
