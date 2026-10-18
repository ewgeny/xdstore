package org.flib.xdstorage.search;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectIdIndexType;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.annotations.XdStorageObjectSearchIndex;
import org.flib.xdstorage.index.XdStorageIndexType;

/** Тестовая сущность поискового движка: политика StoreAsSingleObject с составным индексом. */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsSingleObject)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 4)
@XdStorageObjectSearchIndex(indexName = "probe_idx", indexFieldNames = {"code", "fuel"}, t = 8)
public class SearchEngineProbe {

    @XdStorageObjectId
    private Long id;
    private String code;
    private int fuel;

    public SearchEngineProbe() {
    }

    public SearchEngineProbe(Long id, String code, int fuel) {
        this.id = id;
        this.code = code;
        this.fuel = fuel;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getFuel() { return fuel; }
    public void setFuel(int fuel) { this.fuel = fuel; }
}
