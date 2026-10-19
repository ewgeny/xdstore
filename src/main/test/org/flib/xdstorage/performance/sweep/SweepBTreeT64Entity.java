package org.flib.xdstorage.performance.sweep;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectIdIndexType;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.index.XdStorageIndexType;

/** Sweep entity: BTree id index with fill factor t=64 (fragmented data files, YAML). */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 64)
public class SweepBTreeT64Entity {
    @XdStorageObjectId
    private String id;
    private String payload;
    private long number;

    public SweepBTreeT64Entity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getNumber() { return number; }
    public void setNumber(long number) { this.number = number; }
}
