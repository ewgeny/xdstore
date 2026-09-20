package org.flib.xdstorage.sqlstorage.search.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageSearchIndexRollbackForFirstPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    public XdStorageSearchIndexRollbackForFirstPhaseCommitController(final XdStorageClassInfo clInfo,
                                                                     final XdStorageSQLResourceId resourceId,
                                                                     final String indexName, final XdStorageSQLResourcesManager manager) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();
    }

    private String rolledbackResourceQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        rolledbackResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass)
                .build(transactionsTable);

        final String table = namingService.getSearchIndexTable(cl, indexName);
        final String prevStateTable = namingService.getPrevStateSearchIndexTable(cl, indexName);

        initDeleteQueries(factory, table);
        initSelectInsertQueries(factory, table, prevStateTable);
        initDeletePrevStateQueries(factory, prevStateTable);
    }

    private String deleteReferencesSQL;

    private String deleteFieldsSQL;

    private String deleteChildrenFieldsSQL;

    private void initDeleteQueries(final XdStorageSQLBuilderFactory factory, final String table) throws XdStorageException {
        deleteReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table);

        deleteFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table);

        deleteChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table);
    }

    private String selectInsertReferencesSQL;

    private String selectInsertFieldsSQL;

    private String selectInsertChildrenFieldsSQL;

    private void initSelectInsertQueries(final XdStorageSQLBuilderFactory factory, final String table, final String prevStateTable) throws XdStorageException {
        selectInsertReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, prevStateTable, clInfo);

        selectInsertFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, prevStateTable, clInfo);

        selectInsertChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, prevStateTable, clInfo);
    }

    private String deletePrevStateReferencesSQL;

    private String deletePrevStateFieldsSQL;

    private String deletePrevStateChildrenFieldsSQL;

    private void initDeletePrevStateQueries(final XdStorageSQLBuilderFactory factory, final String prevStateTable) throws XdStorageException {
        deletePrevStateReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, prevStateTable);

        deletePrevStateFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, prevStateTable);

        deletePrevStateChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, prevStateTable);
    }

    public void markResourceAsRolledBack(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass.priority, resourceId, rolledbackResourceQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.ROLLEDBACK);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);
        command.addParameter(XdStorageSearchIndexRecord.class);

        processor.pushCommand(transaction, command);
    }

    public void rollbackIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand deleteReferencesCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deleteReferencesSQL, helper);
        deleteReferencesCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteReferencesCommand);

        final XdStorageSQLCommand deleteFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deleteFieldsSQL, helper);
        deleteFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteFieldsCommand);

        final XdStorageSQLCommand deleteChildrenFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deleteChildrenFieldsSQL, helper);
        deleteChildrenFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteChildrenFieldsCommand);


        final XdStorageSQLCommand selectInsertReferencesCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction.priority, resourceId, selectInsertReferencesSQL, helper);
        selectInsertReferencesCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertReferencesCommand);

        final XdStorageSQLCommand selectInsertFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction.priority, resourceId, selectInsertFieldsSQL, helper);
        selectInsertFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertFieldsCommand);

        final XdStorageSQLCommand selectInsertChildrenFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction.priority, resourceId, selectInsertChildrenFieldsSQL, helper);
        selectInsertChildrenFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertChildrenFieldsCommand);


        final XdStorageSQLCommand deletePrevStateReferencesCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deletePrevStateReferencesSQL, helper);
        deletePrevStateReferencesCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateReferencesCommand);

        final XdStorageSQLCommand deletePrevStateFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deletePrevStateFieldsSQL, helper);
        deletePrevStateFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateFieldsCommand);

        final XdStorageSQLCommand deletePrevStateChildrenFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction.priority, resourceId, deletePrevStateChildrenFieldsSQL, helper);
        deletePrevStateChildrenFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateChildrenFieldsCommand);
    }
}
