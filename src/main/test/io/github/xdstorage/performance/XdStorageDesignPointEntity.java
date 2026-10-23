package io.github.xdstorage.performance;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/**
 * N50K (docs/tasks/2.1.0/20): the design-point probe entity — a representative domain row
 * (numeric score = the PRIMARY indexed key: indexed EQUALS + selective ranges walk the
 * ordered tree) over a single class,
 * fragment size 128 per the task's disclosure. Top-level: codegen requires it.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectSearchIndex(indexName = "probe_idx", indexFieldNames = {"score"}, t = 16)
public class XdStorageDesignPointEntity {

    @XdStorageObjectId
    private Long id;

    private String name;

    private Long score;

    private String label;

    private Long amount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Long getAmount() { return amount; }
    public void setAmount(Long amount) { this.amount = amount; }
}
