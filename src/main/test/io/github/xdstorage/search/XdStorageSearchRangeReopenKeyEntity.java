package io.github.xdstorage.search;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/** BUG-E acceptance entity (docs/tasks/2.1.0/18): a Long search key — the type that does
 *  not survive the node-YAML round-trip at reopen. Top-level: codegen requires it. */

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(indexName = "by_score", indexFieldNames = {"score"})
public class XdStorageSearchRangeReopenKeyEntity {

    @XdStorageObjectId
    private String id;

    private Long score;

    public XdStorageSearchRangeReopenKeyEntity() {
    }

    public XdStorageSearchRangeReopenKeyEntity(final String id, final Long score) {
        this.id = id;
        this.score = score;
    }

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public Long getScore() {
        return score;
    }

    public void setScore(final Long score) {
        this.score = score;
    }
}
