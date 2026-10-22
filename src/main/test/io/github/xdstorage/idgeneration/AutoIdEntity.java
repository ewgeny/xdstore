package io.github.xdstorage.idgeneration;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageObjectFieldProperties;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** T-3a (docs/tasks/2.1.0/06): entities for the id-generation contract suite. */
public final class AutoIdEntity {

    private AutoIdEntity() {}

    /** Long id, default DATABASE_GENERATOR — the Hi-Lo batch generator. */
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class LongId {
        @XdStorageObjectId
        private Long id;
        private String label;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    /** Integer id, default DATABASE_GENERATOR. */
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class IntId {
        @XdStorageObjectId
        private Integer id;

        public Integer getId() { return id; }
        public void setId(Integer id) { this.id = id; }
    }

    /** String id, default DATABASE_GENERATOR — the UUID generator. */
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class StringId {
        @XdStorageObjectId
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    /** String id with the explicit UUID_GENERATOR field property. */
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class ExplicitUuidId {
        @XdStorageObjectId
        @XdStorageObjectFieldProperties(idGeneratorType = XdStorageIdGeneratorType.UUID_GENERATOR)
        private String id;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}
