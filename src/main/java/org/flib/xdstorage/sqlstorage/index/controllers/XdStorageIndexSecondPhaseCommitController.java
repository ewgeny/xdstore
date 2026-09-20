package org.flib.xdstorage.sqlstorage.index.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageIndexSecondPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageTransactionResourceChanges record;

    private final XdStorageClassInfo indexClInfo;

    public XdStorageIndexSecondPhaseCommitController(final XdStorageClassInfo clInfo,
                                                     final XdStorageSQLResourceId resourceId, final String indexName,
                                                     final XdStorageSQLResourcesManager manager,
                                                     final XdStorageTransactionResourceChanges record) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.record = record;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();

        this.indexClInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
    }

    private String updateTransactionQuery;

    private String cleanQuery;

    private String cleanPrevStateQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        final String indexTable = namingService.getIndexTable(cl, indexName);
        final String indexPrevStateTable = namingService.getPrevStateIndexTable(cl, indexName);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        updateTransactionQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass)
                .build(transactionsTable);
        cleanQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateIndexByTransaction).build(indexTable);
        cleanPrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteIndexByTransaction).build(indexPrevStateTable);
    }

    public void markResourceAsFinished(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass.priority, resourceId, updateTransactionQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.FINISHED);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);
        command.addParameter(XdStorageHashIndexRecord.class);

        processor.pushCommand(transaction, command);
    }

    public void unlockIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand cleanCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateIndexByTransaction.priority, resourceId, cleanQuery, helper);
        cleanCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanCommand);

        final XdStorageSQLCommand cleanPrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteIndexByTransaction.priority, resourceId, cleanPrevStateQuery, helper);
        cleanPrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanPrevStateCommand);
    }
}
