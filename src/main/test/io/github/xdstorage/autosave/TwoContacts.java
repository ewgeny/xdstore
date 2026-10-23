package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageAutoSave;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** dedup fixture: two same-type @AutoSave fields that hold the SAME instance */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class TwoContacts {
    @XdStorageObjectId
    private String id;

    @XdStorageAutoSave
    private OrderLine primary;

    @XdStorageAutoSave
    private OrderLine billing;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public OrderLine getPrimary() { return primary; }
    public void setPrimary(OrderLine primary) { this.primary = primary; }
    public OrderLine getBilling() { return billing; }
    public void setBilling(OrderLine billing) { this.billing = billing; }
}
