package org.flib.xdstorage.sqlstorage.index.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class XdStorageIndexPrepareController {

    private final XdStorageServicesLocator services;

    private final XdStorageClassInfo clInfo;

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final Class<?> cl;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    public XdStorageIndexPrepareController(final XdStorageClassInfo clInfo, final XdStorageServicesLocator services,
                                           final XdStorageSQLResourceId resourceId, final String indexName,
                                           final XdStorageSQLResourcesManager manager) {
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

    private String objectTableSQL;

    private String objectPrevStateTableSQL;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        transactionsTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateTable).build(XdStorageTransaction.class, transactionsTable, helper, services.getConfiguration());

        objectTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateIndexTable)
                        .build(cl, namingService.getIndexTable(cl, indexName), helper);
        objectPrevStateTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateIndexTable)
                        .build(cl, namingService.getPrevStateIndexTable(cl, indexName), helper);
    }

    public void createTables(final Connection connection, final XdStorageTransaction transaction) throws SQLException {
        final Statement st = connection.createStatement();
        st.execute(transactionsTableSQL);
        st.execute(objectTableSQL);
        st.execute(objectPrevStateTableSQL);
    }
}
