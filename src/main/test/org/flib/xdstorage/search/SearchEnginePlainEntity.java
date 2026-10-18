package org.flib.xdstorage.search;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

/** Сущность без поискового индекса - для негативных проверок hasIndex. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class SearchEnginePlainEntity {

    @XdStorageObjectId
    private Long id;
    private String value;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
