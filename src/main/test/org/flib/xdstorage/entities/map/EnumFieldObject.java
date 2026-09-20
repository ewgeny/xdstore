package org.flib.xdstorage.entities.map;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class EnumFieldObject {

    @XdStorageObjectId
    private String id;
    private TestEnum value1;
    private TestEnum value2;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TestEnum getValue1() {
        return value1;
    }

    public void setValue1(TestEnum value1) {
        this.value1 = value1;
    }

    public TestEnum getValue2() {
        return value2;
    }

    public void setValue2(TestEnum value2) {
        this.value2 = value2;
    }
}
