package io.github.xdstorage.idgeneration;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class MyFakeIdCounterRecord {

    @XdStorageObjectId
    private Class<?> cl;

    private Long counter;

    public Class<?> getCl() {
        return cl;
    }

    public void setCl(final Class<?> cl) {
        this.cl = cl;
    }

    public Long getCounter() {
        return counter;
    }

    public void setCounter(final Long counter) {
        this.counter = counter;
    }
}
