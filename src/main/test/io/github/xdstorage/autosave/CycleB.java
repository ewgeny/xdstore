package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class CycleB {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private CycleA a;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public CycleA getA() { return a; }
    public void setA(CycleA a) { this.a = a; }
}
