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
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;

public class XdStorageSearchIndexSecondPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    public XdStorageSearchIndexSecondPhaseCommitController(final XdStorageClassInfo clInfo, final XdStorageSQLResourceId resourceId,
                                                           final String indexName, final XdStorageSQLResourcesManager manager,
                                                           final XdStorageTransactionResourceChanges record) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.cl = clInfo.getClazz();
    }

    private String updateTransactionQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        updateTransactionQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass)
                .build(transactionsTable);

        final String table = namingService.getSearchIndexTable(cl, indexName);
        final String prevStateTable = namingService.getPrevStateSearchIndexTable(cl, indexName);

        initCleanQueries(factory, table);
        initDeletePrevStateQueries(factory, prevStateTable);
    }

    private String cleanReferencesSQL;

    private String cleanFieldsSQL;

    private String cleanChildrenFieldsSQL;

    private void initCleanQueries(final XdStorageSQLBuilderFactory factory, final String table) throws XdStorageException {
        cleanReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table);

        cleanFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table);

        cleanChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table);
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

    public void markResourceAsFinished(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass.priority, resourceId, updateTransactionQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.FINISHED);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);
        command.addParameter(XdStorageSearchIndexRecord.class);

        processor.pushCommand(transaction, command);
    }

    public void unlockIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand cleanReferencesCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction.priority, resourceId, cleanReferencesSQL, helper);
        cleanReferencesCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanReferencesCommand);

        final XdStorageSQLCommand cleanFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction.priority, resourceId, cleanFieldsSQL, helper);
        cleanFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanFieldsCommand);

        final XdStorageSQLCommand cleanChildrenFieldsCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction.priority, resourceId, cleanChildrenFieldsSQL, helper);
        cleanChildrenFieldsCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanChildrenFieldsCommand);


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
