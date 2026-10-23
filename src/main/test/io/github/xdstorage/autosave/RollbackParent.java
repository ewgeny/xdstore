package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.List;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class RollbackParent {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private List<OrderLine> lines;

    @XdStorageAutoSave
    private NoIdField broken;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> lines) { this.lines = lines; }
    public NoIdField getBroken() { return broken; }
    public void setBroken(NoIdField broken) { this.broken = broken; }
}
