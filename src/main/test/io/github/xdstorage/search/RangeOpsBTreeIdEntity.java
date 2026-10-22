package io.github.xdstorage.search;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectIdIndexType;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;
import io.github.xdstorage.index.XdStorageIndexType;

/**
 * FP-1b (docs/tasks/2.1.0/10): twin of {@link RangeOpsEntity} with a B+Tree id index —
 * result shaping (ORDER BY / LIMIT / OFFSET / COUNT) must compose identically on both
 * id-index types (Hash is RangeOpsEntity's default).
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 8)
@XdStorageObjectSearchIndex(indexName = "range_bt_idx", indexFieldNames = {"score", "label"}, t = 8)
public class RangeOpsBTreeIdEntity {

    @XdStorageObjectId
    private String id;

    private Long score;

    private String label;

    public RangeOpsBTreeIdEntity() {}

    public RangeOpsBTreeIdEntity(final String id, final Long score, final String label) {
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
