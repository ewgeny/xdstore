package org.flib.xdstorage;
import org.flib.xdstorage.annotations.XdStorageObjectId;

public class BenchmarkEntity {
    @XdStorageObjectId
    private String id;
    private String payload;

    public BenchmarkEntity() {}
    public BenchmarkEntity(String id, String payload) { this.id = id; this.payload = payload; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
