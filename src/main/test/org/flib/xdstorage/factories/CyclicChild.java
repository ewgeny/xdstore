package org.flib.xdstorage.factories;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

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
