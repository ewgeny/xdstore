package org.flib.xdstorage.structure.update;

import org.flib.xdstorage.annotations.XdStorageObjectId;

import java.util.Date;

public class XdStorageUpdateStructureRecord {

    @XdStorageObjectId
    private String name;

    private Date time;

    private XdStorageUpdateStructureResult result;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(final Date time) {
        this.time = time;
    }

    public XdStorageUpdateStructureResult getResult() {
        return result;
    }

    public void setResult(final XdStorageUpdateStructureResult result) {
        this.result = result;
    }
}
