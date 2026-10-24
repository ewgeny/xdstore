package io.github.xdstorage.reliability;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/**
 * W-4a acceptance entity (docs/tasks/2.1.0/32): explicit NUMERIC ids — the journal replay
 * path must restore them to the entity id-field type (the BUG-C lesson applied to the
 * index-journal replay). Top-level: codegen requires it.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class IndexJournalEntity {

    @XdStorageObjectId
    private Long id;

    private String name;

    public IndexJournalEntity() {
    }

    public IndexJournalEntity(final Long id, final String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
