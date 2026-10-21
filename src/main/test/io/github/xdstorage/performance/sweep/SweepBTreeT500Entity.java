package io.github.xdstorage.performance.sweep;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectIdIndexType;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.index.XdStorageIndexType;

/** Sweep entity: BTree id index with fill factor t=500 (fragmented data files, YAML). */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 500)
public class SweepBTreeT500Entity {
    @XdStorageObjectId
    private String id;
    private String payload;
    private long number;

    public SweepBTreeT500Entity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getNumber() { return number; }
    public void setNumber(long number) { this.number = number; }
}
