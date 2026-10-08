package org.flib.xdstorage.entities;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class XdBlackHole {

    @XdStorageObjectId
    private String id;

    private long mass;

    private boolean isEventHorizonActive;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getMass() {
        return mass;
    }

    public void setMass(long mass) {
        this.mass = mass;
    }

    public boolean getIsEventHorizonActive() {
        return isEventHorizonActive;
    }

    public void setIsEventHorizonActive(boolean eventHorizonActive) {
        isEventHorizonActive = eventHorizonActive;
    }
}
