package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.*;

public class XdStorageSqlSearchQuery {

    private final List<XdStoragePair<IXdStorageSqlPrimaryCriterion, IXdStorageSqlCriterion>> queries;

    public XdStorageSqlSearchQuery() {
        this.queries = new LinkedList<>();
    }

    public void or(final IXdStorageSqlPrimaryCriterion primaryCriterion, final IXdStorageSqlCriterion criterion) {
        queries.add(new XdStoragePair<>(primaryCriterion, criterion));
    }

    public String build(final XdStorageSQLResourceNamingService namingService, final XdStorageClassInfo clInfo,
                      final XdStorageSearchIndex index, final IXdStorageSQLTypesHelper helper) throws XdStorageException {
        try {
            final StringBuilder builder = new StringBuilder();

            if (queries.size() > 1) {
                final XdStorageObjectIdField idField = clInfo.getIdField();
                builder.append("SELECT DISTINCT x.").append(idField.getName()).append(" FROM (");
            }

            for (int i = 0; i < queries.size(); ++i) {
                builder.append(build(namingService, helper, clInfo, index, queries.get(i)));
                if (i < queries.size() - 1) {
                    builder.append(" UNION ");
                }
            };

            if (queries.size() > 1) {
                builder.append(") x");
            }

            return builder.toString();
        } catch (final Throwable e) {
            throw new XdStorageException("building search query error", e);
        }
    }

    private String build(final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper,
                       final XdStorageClassInfo clInfo, final XdStorageSearchIndex index,
                       final XdStoragePair<IXdStorageSqlPrimaryCriterion, IXdStorageSqlCriterion> query) {
        final List<XdStoragePair<String, String>> joinTables = new LinkedList<>();
        final List<String> whereClauses = new LinkedList<>();

        final String fieldsTable = namingService.getSearchIndexTable(clInfo.getClazz(), index.getName()) + "_" + XdStorageSearchIndexTableName.OBJECTS_FIELDS.getNameTableSuffix();
        final String tableAlias = "tbl0";
        joinTables.add(new XdStoragePair<>(fieldsTable, tableAlias));

        // preparing query data
        final IXdStorageSqlPrimaryCriterion primaryQuery = query.a;
        if (primaryQuery != null) {
            final XdStorageObjectField primaryField = index.getPrimaryField();
            whereClauses.add(primaryQuery.build(namingService, helper, clInfo, index, tableAlias, primaryField));
        }

        final IXdStorageSqlCriterion criterion = query.b;
        if (criterion != null) {
            whereClauses.add(criterion.build(namingService, helper, clInfo, index, joinTables));
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();

        // building search query
        final StringBuilder builder = new StringBuilder("SELECT DISTINCT ");

        builder.append(tableAlias).append('.').append(idField.getName()).append(' ');
        builder.append("FROM ").append(fieldsTable).append(' ').append(tableAlias).append(' ');
        for (final XdStoragePair<String, String> table : joinTables) {
            if (!table.b.equalsIgnoreCase(tableAlias)) {
                builder.append("LEFT JOIN ").append(table.a).append(' ').append(table.b).append(" ON ")
                        .append(table.b).append('.').append(idField.getName()).append('=')
                        .append(tableAlias).append('.').append(idField.getName()).append(' ');
            }
        }
        if (!whereClauses.isEmpty()) {
            builder.append("WHERE ");
            for (int i = 0; i < whereClauses.size(); ++i) {
                builder.append(whereClauses.get(i));
                if (i < whereClauses.size() - 1) {
                    builder.append(" AND ");
                }
            }
        }

        return builder.toString();
    }
}
