package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageParentObject;

/** the standard embedded shape: StoreWithParentObject + a parent pointer back to the holder */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class EmbeddedLine {
    @XdStorageObjectId
    private String id;

    @XdStorageParentObject
    private EmbeddedOrder parent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public EmbeddedOrder getParent() { return parent; }
    public void setParent(EmbeddedOrder parent) { this.parent = parent; }
}
