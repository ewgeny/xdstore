package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
public class Delivery {
    @XdStorageObjectId
    private String id;

    private String address;

    public Delivery() {}
    public Delivery(final String id, final String address) {
        this.id = id;
        this.address = address;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
