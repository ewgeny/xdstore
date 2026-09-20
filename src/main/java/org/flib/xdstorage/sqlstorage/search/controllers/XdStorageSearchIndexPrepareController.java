package org.flib.xdstorage.sqlstorage.search.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class XdStorageSearchIndexPrepareController {

    private final XdStorageServicesLocator services;

    private final XdStorageClassInfo clInfo;

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final Class<?> cl;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    public XdStorageSearchIndexPrepareController(final XdStorageClassInfo clInfo, final XdStorageServicesLocator services,
                                                 final XdStorageSQLResourceId resourceId,
                                                 final String indexName, final XdStorageSQLResourcesManager manager) {
        this.services = services;
        this.clInfo = clInfo;
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.manager = manager;
        this.cl = clInfo.getClazz();
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
    }

    private String transactionsTableSQL;

    private String referencesTableSQL;

    private String referencesPrevStateTableSQL;

    private String fieldsTableSQL;

    private String fieldsPrevStateTableSQL;

    private String childrenFieldsTableSQL;

    private String childrenFieldsPrevStateTableSQL;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        transactionsTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateTable)
                        .build(XdStorageTransaction.class, transactionsTable, helper, services.getConfiguration());

        final String table = namingService.getSearchIndexTable(cl, indexName);
        final String prevStateTable = namingService.getPrevStateSearchIndexTable(cl, indexName);

        referencesTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, clInfo, helper);
        referencesPrevStateTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, prevStateTable, clInfo, helper);

        fieldsTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, clInfo, helper);
        fieldsPrevStateTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, prevStateTable, clInfo, helper);

        childrenFieldsTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, clInfo, helper);
        childrenFieldsPrevStateTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateSearchIndexTable)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, prevStateTable, clInfo, helper);

    }

    public void createTables(final Connection connection, final XdStorageTransaction transaction) throws SQLException {
        final Statement st = connection.createStatement();
        st.execute(transactionsTableSQL);

        st.execute(referencesTableSQL);
        st.execute(referencesPrevStateTableSQL);

        st.execute(fieldsTableSQL);
        st.execute(fieldsPrevStateTableSQL);

        st.execute(childrenFieldsTableSQL);
        st.execute(childrenFieldsPrevStateTableSQL);
    }
}
