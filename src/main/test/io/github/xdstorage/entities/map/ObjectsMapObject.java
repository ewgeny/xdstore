package io.github.xdstorage.entities.map;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.HashMap;
import java.util.Map;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class ObjectsMapObject {

    @XdStorageObjectId
    private String id;

    private Map<Object, Object> map = new HashMap<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<Object, Object> getMap() {
        return map;
    }

    public void setMap(Map<Object, Object> map) {
        this.map = map;
    }
}
