package org.flib.xdstorage.sqlstorage.fkresource.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class XdStorageCrossDatasourceFkResourcePrepareController {

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    public XdStorageCrossDatasourceFkResourcePrepareController(final XdStorageSQLResourceId resourceId, final XdStorageSQLResourceNamingService namingService,
                                                               final IXdStorageSQLTypesHelper helper, final XdStorageClassInfo parentObjectClassInfo,
                                                               final XdStorageClassInfo childObjectClassInfo) {
        this.resourceId = resourceId;
        this.namingService = namingService;
        this.helper = helper;
        this.parentObjectClassInfo = parentObjectClassInfo;
        this.childObjectClassInfo = childObjectClassInfo;
    }

    private String fkTableQuery;

    private String fkPrevStateTableQuery;

    private String fkQuery;

    private String fkPrevStateQuery;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String fkTable = namingService.getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);
        final String fkPrevStateTable = namingService.getPrevStateCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);

        fkTableQuery =
                factory.getBuilder(XdStorageSQLBuilderName.CreateCrossDatasourceFkTable).build(parentObjectClassInfo, childObjectClassInfo, fkTable, helper);
        fkPrevStateTableQuery =
                factory.getBuilder(XdStorageSQLBuilderName.CreateCrossDatasourceFkTable).build(parentObjectClassInfo, childObjectClassInfo, fkPrevStateTable, helper);


        final String childObjectTable = namingService.getObjectTable(childObjectClassInfo.getClazz());
        final String childObjectPrevTable = namingService.getPrevStateObjectTable(childObjectClassInfo.getClazz());

        fkQuery =
                factory.getBuilder(XdStorageSQLBuilderName.AddForeignKey).build(fkTable, "child_id", childObjectTable, childObjectClassInfo.getIdField().getName(), helper);
        fkPrevStateQuery =
                factory.getBuilder(XdStorageSQLBuilderName.AddForeignKey).build(fkPrevStateTable, "child_id", childObjectPrevTable, childObjectClassInfo.getIdField().getName(), helper);
    }

    public void createTables(final Connection connection, final XdStorageTransaction transaction) throws SQLException {
        final Statement st = connection.createStatement();
        st.execute(fkTableQuery);
        st.execute(fkPrevStateTableQuery);
    }

    public void registerForeignKeyConstraints(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        processor.pushAlterCommand(transaction, new XdStorageSQLCommand(XdStorageSQLBuilderName.AddForeignKey.priority, resourceId, fkQuery, helper, false));
        processor.pushAlterCommand(transaction, new XdStorageSQLCommand(XdStorageSQLBuilderName.AddForeignKey.priority, resourceId, fkPrevStateQuery, helper, false));
    }
}
