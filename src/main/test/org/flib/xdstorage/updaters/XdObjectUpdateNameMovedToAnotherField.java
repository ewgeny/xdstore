package org.flib.xdstorage.updaters;

import org.flib.xdstorage.entities.XdObject;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.structure.update.XdStorageAbstractStructureUpdater;

public class XdObjectUpdateNameMovedToAnotherField extends XdStorageAbstractStructureUpdater<XdObject> {

    public XdObjectUpdateNameMovedToAnotherField() {
        super("XdObjectUpdateNameMovedToAnotherField");
    }

    @Override
    protected XdObject transform(XdStorageIdentifiableObject oldStateOfObject) {
        final XdObject result = new XdObject();
        result.setObjectId((Long) oldStateOfObject.getId());
        if (oldStateOfObject.getProperty("name") != null) {
            result.setName((String) oldStateOfObject.getProperty("name"));
        } else {
            result.setName((String) oldStateOfObject.getProperty("newName"));
        }
        return result;
    }

    @Override
    public Class<?> getDataClass() {
        return XdObject.class;
    }
}
