package org.flib.xdstorage.entities;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectFieldProperties;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.idgeneration.MyFakeIdGenerator;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class XdObject {

    @XdStorageObjectId
    @XdStorageObjectFieldProperties(
            idGeneratorType = XdStorageIdGeneratorType.CUSTOM_GENERATOR,
            idGeneratorClass = MyFakeIdGenerator.class)
    private Long objectId;

    private String name;

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(final Long objectId) {
        this.objectId = objectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
