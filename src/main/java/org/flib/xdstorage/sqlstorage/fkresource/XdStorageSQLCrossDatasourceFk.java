package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;

public class XdStorageSQLCrossDatasourceFk {

    private final XdStorageSQLResourceId parentResourceId;

    private final Object parentObject;

    private final XdStorageSQLResourceId childResourceId;

    private final Object childObject;

    public XdStorageSQLCrossDatasourceFk(final XdStorageSQLResourceId parentResourceId, final Object parentObject,
                                         final XdStorageSQLResourceId childResourceId, final Object childOBject) {
        this.parentResourceId = parentResourceId;
        this.parentObject = parentObject;
        this.childResourceId = childResourceId;
        this.childObject = childOBject;
    }

    public XdStorageSQLResourceId getParentResourceId() {
        return parentResourceId;
    }

    public Object getParentObject() {
        return parentObject;
    }

    public XdStorageSQLResourceId getChildResourceId() {
        return childResourceId;
    }

    public Object getChildObject() {
        return childObject;
    }
}
