package org.flib.xdstorage.sqlstorage.fkresource.reference.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.fkresource.XdStorageSQLCrossDatasourceFk;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageCrossDatasourceReferenceResourceRollbackForFirstPhaseCommitController {

    private final Object resourceId;

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    public XdStorageCrossDatasourceReferenceResourceRollbackForFirstPhaseCommitController(final Object resourceId, final XdStorageServicesLocator services, final XdStorageSQLResourceNamingService namingService,
                                                                                          final IXdStorageSQLTypesHelper helper, final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo childClassInfo) {
        this.resourceId = resourceId;
        this.services = services;
        this.namingService = namingService;
        this.helper = helper;
        this.parentObjectClassInfo = parentClassInfo;
        this.childObjectClassInfo = childClassInfo;
    }

    private String rolledbackResourceQuery;

    private String deleteQuery;

    private String selectInsertQuery;

    private String deletePrevStateQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final String table = namingService.getObjectTable(childObjectClassInfo.getClazz());
        final String prevStateTable = namingService.getPrevStateObjectTable(childObjectClassInfo.getClazz());

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        rolledbackResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass)
                .build(transactionsTable);

        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceReferenceByTransaction).build(table);
        selectInsertQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceFromPrevState)
                .build(childObjectClassInfo, table, prevStateTable);
        deletePrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceReferenceByTransaction).build(prevStateTable);
    }

    public void markResourceAsRolledBack(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass.priority, resourceId, rolledbackResourceQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.ROLLEDBACK);
        command.addParameter(Boolean.TRUE);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(XdStorageSQLCrossDatasourceFk.class);

        processor.pushCommand(transaction, command);
    }

    public void rollbackObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand deleteCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteCrossDatasourceReferenceByTransaction.priority, resourceId, deleteQuery, helper);
        deleteCommand.addParameter(Boolean.TRUE);
        deleteCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteCommand);

        final XdStorageSQLCommand selectInsertCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceFromPrevState.priority, resourceId, selectInsertQuery, helper);
        selectInsertCommand.addParameter(Boolean.TRUE);
        selectInsertCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertCommand);

        final XdStorageSQLCommand deletePrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteCrossDatasourceReferenceByTransaction.priority, resourceId, deletePrevStateQuery, helper);
        deletePrevStateCommand.addParameter(Boolean.TRUE);
        deletePrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateCommand);
    }
}
