package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.List;

public class XdStorageSqlComplexMultiplexor implements IXdStorageSqlCriterion {

    public static final XdStorageSqlComplexMultiplexor AND = new XdStorageSqlComplexMultiplexor(true);

    public static final XdStorageSqlComplexMultiplexor OR = new XdStorageSqlComplexMultiplexor(false);

    private final boolean and;

    private XdStorageSqlComplexMultiplexor(final boolean and) {
        this.and = and;
    }

    @Override
    public String build(final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper,
                      final XdStorageClassInfo clInfo, final XdStorageSearchIndex index,
                      final List<XdStoragePair<String, String>> joinTables) {
        return and ? "AND" : "OR";
    }
}
