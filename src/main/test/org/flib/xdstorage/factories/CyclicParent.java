package org.flib.xdstorage.factories;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

// Тестовые классы с циклической зависимостью для выявления StackOverflowError
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class CyclicParent {
    @XdStorageObjectId
    private Long id;
    private CyclicChild child;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CyclicChild getChild() {
        return child;
    }

    public void setChild(CyclicChild child) {
        this.child = child;
    }
}
