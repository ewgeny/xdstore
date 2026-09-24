package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageObjectId;

public class TestGeneratedEntity {
    @XdStorageObjectId
    private String codeId;
    private int value;

    public String getCodeId() { return codeId; }
    public void setCodeId(String codeId) { this.codeId = codeId; }
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}
