package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageObjectId;

/**
 * Fixture for the wrapper JavaBeans-contract tests: a plain child bean
 * with identity equals (no equals/hashCode override) like the domain entities.
 */
public class ValueChild {

    @XdStorageObjectId
    private Long id;
    private String label;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    @Override
    public String toString() {
        return "ValueChild{id=" + id + ", label='" + label + "'}";
    }
}
