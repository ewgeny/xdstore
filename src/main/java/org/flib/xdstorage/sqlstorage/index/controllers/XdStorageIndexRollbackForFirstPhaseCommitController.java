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
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageIndexRollbackForFirstPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageClassInfo indexClInfo;

    public XdStorageIndexRollbackForFirstPhaseCommitController(final XdStorageClassInfo clInfo,
                                                               final XdStorageSQLResourceId resourceId, final String indexName,
                                                               final XdStorageSQLResourcesManager manager) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();

        this.indexClInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
    }

    private String rolledbackResourceQuery;

    private String deleteQuery;

    private String selectInsertQuery;

    private String deletePrevStateQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        final String indexTable = namingService.getIndexTable(cl, indexName);
        final String indexPrevStateTable = namingService.getPrevStateIndexTable(cl, indexName);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        rolledbackResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass)
                .build(transactionsTable);

        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteIndexByTransaction).build(indexTable);
        selectInsertQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertIndexFromPrevStateByTransaction)
                .build(indexClInfo, indexTable, indexPrevStateTable);
        deletePrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteIndexByTransaction).build(indexPrevStateTable);
    }

    public void markResourceAsRolledBack(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass.priority, resourceId, rolledbackResourceQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.ROLLEDBACK);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);
        command.addParameter(XdStorageHashIndexRecord.class);

        processor.pushCommand(transaction, command);
    }

    public void rollbackIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand deleteCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteIndexByTransaction.priority, resourceId, deleteQuery, helper);
        deleteCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteCommand);

        final XdStorageSQLCommand selectInsertCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertIndexFromPrevStateByTransaction.priority, resourceId, selectInsertQuery, helper);
        selectInsertCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertCommand);

        final XdStorageSQLCommand deletePrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteIndexByTransaction.priority, resourceId, deletePrevStateQuery, helper);
        deletePrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateCommand);
    }
}
