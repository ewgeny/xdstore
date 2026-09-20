package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.List;

public interface IXdStorageSqlCriterion {

    String build(XdStorageSQLResourceNamingService namingService, IXdStorageSQLTypesHelper helper,
                 XdStorageClassInfo clInfo, XdStorageSearchIndex index, List<XdStoragePair<String, String>> joinTables);
}
