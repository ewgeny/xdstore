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
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.Map;

public class XdStorageCrossDatasourceFkResourceSecondPhaseCommitController {

    private final Object resourceId;

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final XdStorageClassInfo parentObjectClassInfo;

    private final XdStorageClassInfo childObjectClassInfo;

    public XdStorageCrossDatasourceFkResourceSecondPhaseCommitController(final Object resourceId, final XdStorageServicesLocator services, final XdStorageSQLResourceNamingService namingService,
                                                                        final IXdStorageSQLTypesHelper helper, final XdStorageClassInfo parentClassInfo, final XdStorageClassInfo childClassInfo) {
        this.resourceId = resourceId;
        this.services = services;
        this.namingService = namingService;
        this.helper = helper;
        this.parentObjectClassInfo = parentClassInfo;
        this.childObjectClassInfo = childClassInfo;
    }

    private String updateTransactionQuery;

    private String cleanQuery;

    private String cleanPrevStateQuery;

    private Map<XdStorageObjectField, XdStoragePair<String, String>> internalFieldTables;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final String fkTable = namingService.getCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);
        final String fkPrevStateTable = namingService.getPrevStateCrossDatasourceFkTable(parentObjectClassInfo.getClazz(), childObjectClassInfo.getClazz(), XdStorageSQLCrossDatasourceFk.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        updateTransactionQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass)
                .build(transactionsTable);
        cleanQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateCrossDatasourceFkByTransaction).build(fkTable);
        cleanPrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction).build(fkPrevStateTable);
    }

    public void markResourceAsFinished(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass.priority, resourceId, updateTransactionQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.FINISHED);
        command.addParameter(Boolean.FALSE);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(XdStorageSQLCrossDatasourceFk.class);

        processor.pushCommand(transaction, command);
    }

    public void unlockObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand cleanCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateCrossDatasourceFkByTransaction.priority, resourceId, cleanQuery, helper);
        cleanCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanCommand);

        final XdStorageSQLCommand cleanPrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction.priority, resourceId, cleanPrevStateQuery, helper);
        cleanPrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanPrevStateCommand);
    }
}
