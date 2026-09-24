package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.annotations.XdStorageObjectId;

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
