package org.flib.xdstorage.sqlstorage.sql.processor;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class XdStorageDefaultSQLParametersProviderAndIdConsumer extends XdStorageDefaultSQLParametersProvider
        implements IXdStorageSQLIdConsumer {

    private final List<Object> idConsumers;

    private final IXdStorageSQLSetIdFunction setIdFunction;

    public XdStorageDefaultSQLParametersProviderAndIdConsumer(final IXdStorageSQLSetIdFunction setIdFunction) {
        idConsumers = new ArrayList<>();
        this.setIdFunction = setIdFunction;
    }

    public void addConsumer(final Object object) {
        idConsumers.add(object);
    }

    @Override
    public void setId(int rowIndex, ResultSet rs) {
        final Object object = idConsumers.get(rowIndex);
        setIdFunction.setId(object, rs);
    }
}
