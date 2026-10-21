package io.github.xdstorage.code;

import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;
import io.github.xdstorage.annotations.XdStorageLoadByGetMethod; // ИМПОРТ
import io.github.xdstorage.XdStoragePolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class ComplexEntityForGeneration {
    @XdStorageObjectId
    private Long id;
    private String name;

    @XdStorageLoadByGetMethod // ОПТИМИЗАЦИЯ: Активируем ленивую подгрузку для тестов
    private java.util.List<String> tags;

    @XdStorageLoadByGetMethod // ОПТИМИЗАЦИЯ
    private java.util.Map<String, Object> metadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }
    public java.util.Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(java.util.Map<String, Object> metadata) { this.metadata = metadata; }
}
