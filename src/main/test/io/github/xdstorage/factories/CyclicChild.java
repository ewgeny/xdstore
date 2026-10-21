package io.github.xdstorage.factories;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class CyclicChild {
    @XdStorageObjectId
    private Long id;
    private CyclicParent parent;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CyclicParent getParent() {
        return parent;
    }

    public void setParent(CyclicParent parent) {
        this.parent = parent;
    }
}
