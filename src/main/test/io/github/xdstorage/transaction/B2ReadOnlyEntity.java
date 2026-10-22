package io.github.xdstorage.transaction;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** B2 contract-test entity: default Hash id index — the path the read-only commit short path targets. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class B2ReadOnlyEntity {

    @XdStorageObjectId
    private String id;
    private String payload;

    public B2ReadOnlyEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
