package io.github.xdstorage.trigger;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** T-3a (docs/tasks/2.1.0/06): entity for the trigger facade suite. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class TriggeredEntity {

    @XdStorageObjectId
    private String id;

    private Long value;

    public TriggeredEntity() {}

    public TriggeredEntity(final String id, final Long value) {
        this.id = id;
        this.value = value;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getValue() { return value; }
    public void setValue(Long value) { this.value = value; }
}
