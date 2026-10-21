package io.github.xdstorage.search;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageObjectSearchIndex;

/** Тестовая сущность: дочерний объект (StoreWithParentObject) поискового движка. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
@XdStorageObjectSearchIndex(indexName = "crew_idx", indexFieldNames = {"rank"}, t = 8)
public class SearchEngineCrewMember {

    @XdStorageObjectId
    private Long id;
    private String rank;

    public SearchEngineCrewMember() {
    }

    public SearchEngineCrewMember(Long id, String rank) {
        this.id = id;
        this.rank = rank;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }
}
