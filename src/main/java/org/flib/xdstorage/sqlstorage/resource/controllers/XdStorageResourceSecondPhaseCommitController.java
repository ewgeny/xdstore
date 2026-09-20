package org.flib.xdstorage.sqlstorage.resource.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.resource.helpers.XdStorageResourceSecondPhaseInternalObjectsHelper;
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
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XdStorageResourceSecondPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields;

    private final XdStorageTransactionResourceChanges record;

    public XdStorageResourceSecondPhaseCommitController(final Class<?> cl,
                                                        final XdStorageSQLResourceId resourceId, final XdStorageSQLResourcesManager manager,
                                                        final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields,
                                                        final XdStorageTransactionResourceChanges record) {
        this.resourceId = resourceId;
        this.record = record;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.cl = cl;
        this.clInfo = XdStorageObjectUtils.getClassInfo(cl);

        this.internalObjectsFields = internalObjectsFields;
    }

    private Map<XdStorageObjectField, Collection<Object>> links;

    public void collectLinks() {
        links = new HashMap<>();
        XdStorageResourceSecondPhaseInternalObjectsHelper.collectLinksForSecondPhaseCommit(record, links, helper);
    }

    private String transactionsTable;

    private String objectTable;

    private String objectPrevStateTable;

    private Map<XdStorageObjectField, XdStoragePair<String, String>> internalFieldTables;

    public void initNamesOfTables() {
        transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        objectTable = namingService.getObjectTable(cl);
        objectPrevStateTable = namingService.getPrevStateObjectTable(cl);

        internalFieldTables = new HashMap<>();
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();

            final XdStorageClassInfo clInfoInternal = XdStorageObjectUtils.getClassInfo(field.getFieldInfo().getValueClass());

            final String linksTable, linksPrevStateTable;
            if (clInfoInternal.getIdField() != null) {
                linksTable = namingService.getObjectLinksTable(cl, field.getName());
                linksPrevStateTable = namingService.getObjectPrevStateLinksTable(cl, field.getName());
            } else {
                linksTable = namingService.getInternalObjectsTable(cl, field.getName());
                linksPrevStateTable = namingService.getInternalObjectsPrevStateTable(cl, field.getName());
            }
            internalFieldTables.put(field, new XdStoragePair<>(linksTable, linksPrevStateTable));
        }
    }

    private String updateTransactionQuery;

    private String cleanQuery;

    private String cleanPrevStateQuery;

    private Map<XdStorageObjectField, XdStoragePair<String, String>> linksQueries;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        updateTransactionQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass)
                .build(transactionsTable);
        cleanQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateByTransaction).build(objectTable);
        cleanPrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteByTransaction).build(objectPrevStateTable);

        linksQueries = new HashMap<>();
        for (final XdStorageObjectField field : internalObjectsFields.keySet()) {
            final XdStoragePair<String, String> tables = internalFieldTables.get(field);

            final XdStorageClassInfo clInfoInternal = XdStorageObjectUtils.getClassInfo(field.getFieldInfo().getValueClass());

            final String updateLinksQuery, cleanPrevStateLinksQuery;
            if (clInfoInternal.getIdField() != null) {
                updateLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateLinksByTransaction)
                        .build(clInfo, tables.a);
                cleanPrevStateLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteLinksByTransactionAndId)
                        .build(clInfo, tables.b);
            } else {
                updateLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateInternalObjectsByTransaction)
                        .build(clInfo, tables.a);
                cleanPrevStateLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteInternalObjectsByTransaction)
                        .build(clInfo, tables.b);
            }
            linksQueries.put(field, new XdStoragePair<>(updateLinksQuery, cleanPrevStateLinksQuery));
        }
    }

    public void markResourceAsFinished(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass.priority, resourceId, updateTransactionQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.FINISHED);
        command.addParameter(Boolean.TRUE);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);

        processor.pushCommand(transaction, command);
    }

    public void unlockObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand cleanCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateByTransaction.priority, resourceId, cleanQuery, helper);
        cleanCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanCommand);

        final XdStorageSQLCommand cleanPrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteByTransaction.priority, resourceId, cleanPrevStateQuery, helper);
        cleanPrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, cleanPrevStateCommand);
    }

    public void unlockLinks(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final String transactionId = transaction.getTransactionId();

        for (final Map.Entry<XdStorageObjectField, Collection<Object>> link : links.entrySet()) {
            final XdStorageObjectField field = link.getKey();

            // preparing batch parameters
            final XdStorageDefaultSQLParametersProvider updateLinksParameters = new XdStorageDefaultSQLParametersProvider();
            final XdStorageDefaultSQLParametersProvider cleanPrevStateLinksParameters = new XdStorageDefaultSQLParametersProvider();
            for (final Object objectId : link.getValue()) {
                final List<Object> updateLinksRow = updateLinksParameters.addRow();
                updateLinksRow.add(transactionId);
                updateLinksRow.add(objectId);

                final List<Object> cleanPrevStateLinksRow = cleanPrevStateLinksParameters.addRow();
                cleanPrevStateLinksRow.add(transactionId);
                cleanPrevStateLinksRow.add(objectId);
            }

            // building batches
            final XdStoragePair<String, String> queries = linksQueries.get(field);

            final XdStorageClassInfo clInfoInternal = XdStorageObjectUtils.getClassInfo(field.getFieldInfo().getValueClass());
            final XdStorageSQLBatch updateLinksBatch;
            final XdStorageSQLBatch cleanPrevStateLinksBatch;
            if (clInfoInternal.getIdField() != null) {
                updateLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.UpdateLinksByTransaction.priority, resourceId, queries.a, updateLinksParameters, helper);
                cleanPrevStateLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteLinksByTransactionAndId.priority, resourceId, queries.b, cleanPrevStateLinksParameters, helper);
            } else {
                updateLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.UpdateInternalObjectsByTransaction.priority, resourceId, queries.a, updateLinksParameters, helper);
                cleanPrevStateLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteInternalObjectsByTransaction.priority, resourceId, queries.b, cleanPrevStateLinksParameters, helper);
            }

            // registering batches for execution
            processor.pushCommand(transaction, updateLinksBatch);
            processor.pushCommand(transaction, cleanPrevStateLinksBatch);
        }
    }
}
