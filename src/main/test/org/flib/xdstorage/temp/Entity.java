package org.flib.xdstorage.temp;

import org.flib.xdstorage.annotations.XdStorageObjectId;

public class Entity {

    @XdStorageObjectId
    private String id;

    private String name;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
