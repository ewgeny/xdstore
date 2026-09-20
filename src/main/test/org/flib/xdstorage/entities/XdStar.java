package org.flib.xdstorage.entities;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.annotations.XdStorageObjectSearchIndex;
import org.flib.xdstorage.annotations.XdStorageParentObject;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
@XdStorageObjectSearchIndex(indexName = "test_star_index", indexFieldNames = {"name"}, t = 50)
public class XdStar {

    @XdStorageObjectId
    private String id;

    private String name;

    @XdStorageParentObject
    private XdStarSystem parent;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public XdStarSystem getParent() {
        return parent;
    }

    public void setParent(XdStarSystem parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("XdStar { ");
        sb.append(" id: ").append(id);
        sb.append(", name: ").append(name);
        sb.append(" }");
        return sb.toString();
    }
}
