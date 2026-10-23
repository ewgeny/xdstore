package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

import java.util.List;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class OrderLine {
    @XdStorageObjectId
    private String id;

    private String sku;

    @XdStorageAutoSave
    private List<LineNote> notes;

    public OrderLine() {}
    public OrderLine(final String id, final String sku) {
        this.id = id;
        this.sku = sku;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public List<LineNote> getNotes() { return notes; }
    public void setNotes(List<LineNote> notes) { this.notes = notes; }
}
