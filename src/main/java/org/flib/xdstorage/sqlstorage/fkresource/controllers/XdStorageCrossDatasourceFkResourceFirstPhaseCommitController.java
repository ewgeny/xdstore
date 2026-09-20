package org.flib.xdstorage.sqlstorage.fkresource.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.processor.*;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;

import java.util.Collection;
import java.util.List;

public class XdStorageCrossDatasourceFkResourceFirstPhaseCommitController {

    private final Object resourceId;

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    private final XdStorageTransactionResourceChanges changes;

    public XdStorageCrossDatasourceFkResourceFirstPhaseCommitController(final Object resourceId, final XdStorageServicesLocator services, final XdStorageSQLResourceNamingService namingService,
                                                                        final IXdStorageSQLTypesHelper helper, final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo childClassInfo,
                                                                        final XdStorageTransactionResourceChanges changes) {
        this.resourceId = resourceId;
        this.services = services;
        this.namingService = namingService;
        this.helper = helper;
        this.parentObjectClassInfo = parentClassInfo;
        this.childObjectClassInfo = childClassInfo;
        this.changes = changes;
    }

    private String registerResourceQuery;

    private String insertQuery;

    private String deleteQuery;

    private String insertForDeleteQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final String fkTable = namingService.getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);
        final String fkPrevStateTable = namingService.getPrevStateCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();
        registerResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.RegisterResource).build(transactionsTable);

        insertQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertCrossDatasourceFk).build(fkTable, helper);
        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceFk).build(fkTable, helper);
        insertForDeleteQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkForDelete).build(fkTable, fkPrevStateTable);
    }


    public void registerResource(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.RegisterResource.priority, resourceId, registerResourceQuery, helper);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(transaction.getTimestart());
        command.addParameter(XdStorageSQLCrossDatasourceFk.class);
        command.addParameter(null);
        command.addParameter(Boolean.FALSE);
        command.addParameter(XdStorageCommitTransactionState.PREPARED);

        processor.pushCommand(transaction, command);
    }

    public void storeForeignKeys(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageDefaultSQLParametersProvider insertParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 4;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                if (parameterIndex < 3) {
                    final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                    switch (parameterIndex) {
                        case 0:
                            return fk.getParentResourceId().getDataSource();
                        case 1:
                            return parentObjectClassInfo.getIdField().get(fk.getParentObject());
                        case 2:
                            return childObjectClassInfo.getIdField().get(fk.getChildObject());
                    }
                }
                return super.getParameter(rowIndex, 1);
            }
        };
        final XdStorageSQLBatch insertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertCrossDatasourceFk.priority,
                resourceId, insertQuery, insertParametersProvider, helper);
        processor.pushCommand(transaction, insertBatch);

        final XdStorageDefaultSQLParametersProvider deleteParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 2;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                switch (parameterIndex) {
                    case 0:
                        return parentObjectClassInfo.getIdField().get(fk.getParentObject());
                    case 1:
                        return childObjectClassInfo.getIdField().get(fk.getChildObject());
                }
                return null;
            }
        };
        final XdStorageSQLBatch deleteBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteCrossDatasourceFk.priority,
                resourceId, deleteQuery, deleteParametersProvider, helper);
        processor.pushCommand(transaction, deleteBatch);

        final XdStorageDefaultSQLParametersProvider selectInsertParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 3;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                switch (parameterIndex) {
                    case 0:
                        return super.getParameter(rowIndex, 1);
                    case 1:
                        return parentObjectClassInfo.getIdField().get(fk.getParentObject());
                    case 2:
                        return childObjectClassInfo.getIdField().get(fk.getChildObject());
                }
                return null;
            }
        };
        final XdStorageSQLBatch selectInsertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkForDelete.priority,
                resourceId, insertForDeleteQuery, selectInsertParametersProvider, helper);
        processor.pushCommand(transaction, selectInsertBatch);

        // preparing batch parameters
        changes.getChangesObjects().forEach(change -> {
            if (change.type == XdStorageObjectOperationType.Insert) {
                List<Object> row = insertParametersProvider.addRow();
                row.add(change.newObject);
                row.add(transaction.getTransactionId());
            } else {
                List<Object> row = deleteParametersProvider.addRow();
                row.add(change.oldObject);

                row = selectInsertParametersProvider.addRow();
                row.add(change.oldObject);
                row.add(transaction.getTransactionId());
            }
        });
    }
}
