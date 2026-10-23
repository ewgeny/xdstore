package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class CycleA {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private CycleB b;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public CycleB getB() { return b; }
    public void setB(CycleB b) { this.b = b; }
}
