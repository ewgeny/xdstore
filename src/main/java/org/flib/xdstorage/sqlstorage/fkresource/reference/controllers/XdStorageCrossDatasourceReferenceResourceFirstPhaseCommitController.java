package org.flib.xdstorage.sqlstorage.fkresource.reference.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageDefaultSQLParametersProvider;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLBatch;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.List;

public class XdStorageCrossDatasourceReferenceResourceFirstPhaseCommitController {

    private final Object resourceId;

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    private final XdStorageTransactionResourceChanges changes;

    public XdStorageCrossDatasourceReferenceResourceFirstPhaseCommitController(final Object resourceId, final XdStorageServicesLocator services, final XdStorageSQLResourceNamingService namingService,
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

        final String table = namingService.getObjectTable(childObjectClassInfo.getClazz());
        final String prevStateTable = namingService.getPrevStateObjectTable(childObjectClassInfo.getClazz());

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();
        registerResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.RegisterResource).build(transactionsTable);

        insertQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertCrossDatasourceReference).build(childObjectClassInfo, table);
        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceReference).build(childObjectClassInfo, table);
        insertForDeleteQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceForDelete).build(childObjectClassInfo, table, prevStateTable);
    }


    public void registerResource(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.RegisterResource.priority, resourceId, registerResourceQuery, helper);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(transaction.getTimestart());
        command.addParameter(childObjectClassInfo.getClazz());
        command.addParameter(null);
        command.addParameter(Boolean.TRUE);
        command.addParameter(XdStorageCommitTransactionState.PREPARED);

        processor.pushCommand(transaction, command);
    }

    public void storeReferences(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageDefaultSQLParametersProvider insertParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 4;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                if (parameterIndex == 0) {
                    final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                    return childObjectClassInfo.getIdField().get(fk.getChildObject());
                } else if (parameterIndex == 2) {
                    final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 2);
                    return fk.getChildResourceId().getDataSource();
                }
                return super.getParameter(rowIndex, parameterIndex);
            }
        };
        final XdStorageSQLBatch insertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertCrossDatasourceReference.priority,
                resourceId, insertQuery, insertParametersProvider, helper);
        processor.pushCommand(transaction, insertBatch);

        final XdStorageDefaultSQLParametersProvider deleteParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 1;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                return childObjectClassInfo.getIdField().get(fk.getChildObject());
            }
        };
        final XdStorageSQLBatch deleteBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteCrossDatasourceReference.priority,
                resourceId, deleteQuery, deleteParametersProvider, helper);
        processor.pushCommand(transaction, deleteBatch);

        final XdStorageDefaultSQLParametersProvider selectInsertParametersProvider = new XdStorageDefaultSQLParametersProvider() {
            @Override
            public int getCountParameters(final int rowIndex) {
                return 2;
            }

            @Override
            public Object getParameter(final int rowIndex, final int parameterIndex) {
                switch (parameterIndex) {
                    case 0:
                        return super.getParameter(rowIndex, 1);
                    case 1:
                        final XdStorageSQLCrossDatasourceFk fk = (XdStorageSQLCrossDatasourceFk) super.getParameter(rowIndex, 0);
                        return childObjectClassInfo.getIdField().get(fk.getChildObject());
                }
                return null;
            }
        };
        final XdStorageSQLBatch selectInsertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceForDelete.priority,
                resourceId, insertForDeleteQuery, selectInsertParametersProvider, helper);
        processor.pushCommand(transaction, selectInsertBatch);

        // preparing batch parameters
        changes.getChangesObjects().forEach(change -> {
            if (change.type == XdStorageObjectOperationType.Insert) {
                List<Object> row = insertParametersProvider.addRow();
                row.add(change.newObject);
                row.add(Boolean.TRUE);
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
