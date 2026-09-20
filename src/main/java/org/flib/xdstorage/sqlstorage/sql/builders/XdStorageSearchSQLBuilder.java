package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageSearchSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... params) throws XdStorageException {
        return buildInternal(cast(params[0]), cast(params[1]), cast(params[2]), cast(params[3]), cast(params[4]));
    }

    private String buildInternal(final XdStorageClassInfo clInfo, final String indexName, final XdStorageSqlSearchQuery query,
                                 final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper) throws XdStorageException {

        return query.build(namingService, clInfo, clInfo.getIndexes().get(indexName), helper);
    }
}
