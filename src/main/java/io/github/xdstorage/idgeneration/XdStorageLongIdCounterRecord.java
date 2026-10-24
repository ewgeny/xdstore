package io.github.xdstorage.idgeneration;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/**
 * The persisted Hi-Lo counter record for Long ids: one record per entity class,
 * its value marking the last id batch the engine handed out (an engine-internal
 * stored entity — not an application API). The no-arg constructor is part of the
 * stored-entity contract: the engine materializes records through it.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class XdStorageLongIdCounterRecord {

    /** Required by the stored-entity contract: the engine materializes records through it. */
    public XdStorageLongIdCounterRecord() {
    }

    @XdStorageObjectId
    private Class<?> cl;

    private Long counter;

    /** The served entity class.
     *
     * @return the entity class this counter record serves (the record's id) */
    public Class<?> getCl() {
        return cl;
    }

    /** Sets the served entity class.
     *
     * @param cl the entity class this record serves */
    public void setCl(final Class<?> cl) {
        this.cl = cl;
    }

    /** The batch boundary.
     *
     * @return the last id batch boundary handed out (Hi-Lo high water mark) */
    public Long getCounter() {
        return counter;
    }

    /** Advances the batch boundary.
     *
     * @param counter the new batch boundary */
    public void setCounter(final Long counter) {
        this.counter = counter;
    }
}
