package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.LinkedList;
import java.util.List;

public class XdStorageSqlComplexCriterion implements IXdStorageSqlCriterion {

    private final List<IXdStorageSqlCriterion> criterions;

    public XdStorageSqlComplexCriterion(final IXdStorageSqlCriterion criterion) {
        this.criterions = new LinkedList<>();
        this.criterions.add(criterion);
    }

    public XdStorageSqlComplexCriterion and(final IXdStorageSqlCriterion criterion) {
        criterions.add(XdStorageSqlComplexMultiplexor.AND);
        criterions.add(criterion);
        return this;
    }

    public XdStorageSqlComplexCriterion or(final IXdStorageSqlCriterion criterion) {
        criterions.add(XdStorageSqlComplexMultiplexor.OR);
        criterions.add(criterion);
        return this;
    }

    @Override
    public String build(final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper,
                      final XdStorageClassInfo clInfo, final XdStorageSearchIndex index,
                      final List<XdStoragePair<String, String>> joinTables) {

        final StringBuilder builder = new StringBuilder("( ");

        for (IXdStorageSqlCriterion criterion : criterions) {
            builder.append(criterion.build(namingService, helper, clInfo, index, joinTables)).append(' ');
        }

        return builder.append(')').toString();
    }
}
