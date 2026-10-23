package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.List;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class EmbeddedOrder {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private List<EmbeddedLine> lines;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<EmbeddedLine> getLines() { return lines; }
    public void setLines(List<EmbeddedLine> lines) { this.lines = lines; }
}
