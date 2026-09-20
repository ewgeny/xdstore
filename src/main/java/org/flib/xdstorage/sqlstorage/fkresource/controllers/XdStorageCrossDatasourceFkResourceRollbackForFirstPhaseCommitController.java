package org.flib.xdstorage.sqlstorage.fkresource.controllers;

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

public class XdStorageCrossDatasourceFkResourceRollbackForFirstPhaseCommitController {

    private final Object resourceId;

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    public XdStorageCrossDatasourceFkResourceRollbackForFirstPhaseCommitController(final Object resourceId, final XdStorageServicesLocator services, final XdStorageSQLResourceNamingService namingService,
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

        final String fkTable = namingService.getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);
        final String fkPrevStateTable = namingService.getPrevStateCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        rolledbackResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass)
                .build(transactionsTable);

        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction).build(fkTable);
        selectInsertQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkFromPrevState)
                .build(fkTable, fkPrevStateTable);
        deletePrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction).build(fkPrevStateTable);
    }

    public void markResourceAsRolledBack(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass.priority, resourceId, rolledbackResourceQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.ROLLEDBACK);
        command.addParameter(Boolean.FALSE);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(XdStorageSQLCrossDatasourceFk.class);

        processor.pushCommand(transaction, command);
    }

    public void rollbackObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand deleteCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction.priority, resourceId, deleteQuery, helper);
        deleteCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteCommand);

        final XdStorageSQLCommand selectInsertCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkFromPrevState.priority, resourceId, selectInsertQuery, helper);
        selectInsertCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertCommand);

        final XdStorageSQLCommand deletePrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction.priority, resourceId, deletePrevStateQuery, helper);
        deletePrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateCommand);
    }
}
