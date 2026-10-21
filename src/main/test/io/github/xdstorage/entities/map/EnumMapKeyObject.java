package io.github.xdstorage.entities.map;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.Map;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class EnumMapKeyObject {

    @XdStorageObjectId
    private String id;
    private Map<TestEnum, Object> map;

    private EnumFieldObject fieldObject;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<TestEnum, Object> getMap() {
        return map;
    }

    public void setMap(Map<TestEnum, Object> map) {
        this.map = map;
    }

    public EnumFieldObject getFieldObject() {
        return fieldObject;
    }

    public void setFieldObject(EnumFieldObject fieldObject) {
        this.fieldObject = fieldObject;
    }
}
