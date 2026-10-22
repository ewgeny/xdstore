package io.github.xdstorage.search;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/** FP-1a boundary-suite entity: search index over a Long score (primary) + a String label. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(indexName = "range_idx", indexFieldNames = {"score", "label"}, t = 8)
public class RangeOpsEntity {

    @XdStorageObjectId
    private String id;

    private Long score;

    private String label;

    public RangeOpsEntity() {}

    public RangeOpsEntity(final String id, final Long score, final String label) {
        this.id = id;
        this.score = score;
        this.label = label;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
