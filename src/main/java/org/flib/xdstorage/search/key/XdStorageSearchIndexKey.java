package org.flib.xdstorage.search.key;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class XdStorageSearchIndexKey implements Comparable<XdStorageSearchIndexKey> {

    private Object value;

    private Object objectId;

    private XdStorageSearchIndexOperationType operation;

    public XdStorageSearchIndexKey() {

    }

    public XdStorageSearchIndexKey(final Object value, final Object objectId) {
        this(value, objectId, null);
    }

    public XdStorageSearchIndexKey(final Object value, final Object objectId, final XdStorageSearchIndexOperationType operation) {
        this.value = value;
        this.objectId = objectId;
        this.operation = operation;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    public Object getObjectId() {
        return objectId;
    }

    public void setObjectId(final Object objectId) {
        this.objectId = objectId;
    }

    public void setOperation(final XdStorageSearchIndexOperationType operation) {
        this.operation = operation;
    }

    @Override
    public int compareTo(final XdStorageSearchIndexKey o) {
        int result;
        final XdStorageSearchIndexOperationType op = operation == null ? o.operation : operation;
        switch (op) {
            case Insert:
            case Update:
            case Delete:
                result = value == null ? o.value == null ? 0 : -1 : o.value == null ? 1 : ((Comparable)value).compareTo(o.value);
                result = result == 0 ? ((Comparable)objectId).compareTo(o.objectId) : result;
                break;
            case Search:
                result = value == null ? o.value == null ? 0 : -1 : o.value == null ? 1 : ((Comparable)value).compareTo(o.value); // TODO implement applying search rule for value ? (equals, startsWith for strings)
                break;
            case Watch:
                result = 0; // shows all keys are watchable
                break;
            default:
                result = -1;
        }
        return result;
    }

    @Override
    public String toString() {
        return objectId.toString();
    }
}
