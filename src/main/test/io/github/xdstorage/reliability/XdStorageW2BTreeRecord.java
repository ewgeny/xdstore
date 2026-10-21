package io.github.xdstorage.reliability;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectIdIndexType;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.index.XdStorageIndexType;

/**
 * W-2: BTree id index with small fill factor t=8 — 200+ ключей дают многоуровневое дерево,
 * что проверяет материализацию стабов корня, детей и починку бэк-референсов после реопена.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 8)
public class XdStorageW2BTreeRecord {
    @XdStorageObjectId
    private String id;
    private String payload;
    private long number;

    public XdStorageW2BTreeRecord() {}

    public XdStorageW2BTreeRecord(final String id, final String payload, final long number) {
        this.id = id;
        this.payload = payload;
        this.number = number;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public long getNumber() { return number; }
    public void setNumber(long number) { this.number = number; }
}
