package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** transitive-cascade fixture: a grandchild reached through OrderLine's own @XdStorageAutoSave */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class LineNote {
    @XdStorageObjectId
    private String id;

    private String text;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
