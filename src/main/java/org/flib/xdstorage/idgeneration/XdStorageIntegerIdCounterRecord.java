package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class XdStorageIntegerIdCounterRecord {

    @XdStorageObjectId
    private Class<?> cl;

    private Integer counter;

    public Class<?> getCl() {
        return cl;
    }

    public void setCl(final Class<?> cl) {
        this.cl = cl;
    }

    public Integer getCounter() {
        return counter;
    }

    public void setCounter(final Integer counter) {
        this.counter = counter;
    }
}
