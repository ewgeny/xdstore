package org.flib.xdstorage.rules;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.entities.XdPlanet;
import org.flib.xdstorage.sqlstorage.resource.IXdStorageSQLDataSourceRule;

public class XdDataSourceRule1 implements IXdStorageSQLDataSourceRule {

    private IXdStorage storage;

    @Override
    public void setStorage(final IXdStorage storage) {
        this.storage = storage;
    }

    @Override
    public boolean passed(final Object object) {
        if(object != null) {
            final XdPlanet planet = (XdPlanet) object;
            return planet.getName() != null ? planet.getName().contains("1") : false;
        }
        return false;
    }
}
