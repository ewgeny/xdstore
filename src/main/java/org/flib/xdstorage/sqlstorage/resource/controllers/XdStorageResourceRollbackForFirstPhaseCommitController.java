package org.flib.xdstorage.sqlstorage.resource.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
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
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.HashMap;
import java.util.Map;

public class XdStorageResourceRollbackForFirstPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields;

    public XdStorageResourceRollbackForFirstPhaseCommitController(final Class<?> cl,
                                                                  final XdStorageSQLResourceId resourceId, final XdStorageSQLResourcesManager manager,
                                                                  final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields) {
        this.resourceId = resourceId;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.cl = cl;
        this.clInfo = XdStorageObjectUtils.getClassInfo(cl);

        this.internalObjectsFields = internalObjectsFields;
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

    private String rolledbackResourceQuery;

    private String deleteQuery;

    private String selectInsertQuery;

    private String deletePrevStateQuery;

    private Map<XdStorageObjectField, String> deleteLinksQuery;

    private Map<XdStorageObjectField, String> selectInsertLinksQuery;

    private Map<XdStorageObjectField, String> deletePrevStateLinksQuery;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        rolledbackResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass)
                .build(transactionsTable);

        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteByTransaction).build(objectTable);
        selectInsertQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertFromPrevStateByTransaction)
                .build(clInfo, objectTable, objectPrevStateTable, helper);
        deletePrevStateQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteByTransaction).build(objectPrevStateTable);

        deleteLinksQuery = new HashMap<>();
        selectInsertLinksQuery = new HashMap<>();
        deletePrevStateLinksQuery = new HashMap<>();
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();
            final XdStoragePair<String, String> tables = internalFieldTables.get(field);

            final XdStorageClassInfo clInfoInternal;
            final Class<?> mapKeyClass;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
                mapKeyClass = null;
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
                mapKeyClass = classes[0];
            }

            deleteLinksQuery.put(field, factory.getBuilder(XdStorageSQLBuilderName.DeleteLinksByTransaction).build(tables.a));
            if (clInfoInternal.getIdField() != null) {
                selectInsertLinksQuery.put(field, factory.getBuilder(XdStorageSQLBuilderName.SelectInsertLinksByTransaction)
                        .build(clInfo, tables.a, tables.b, field.getName(), classes));
            } else {
                selectInsertLinksQuery.put(field, factory.getBuilder(XdStorageSQLBuilderName.SelectInsertInternalObjectsByTransaction)
                        .build(clInfo, clInfoInternal, mapKeyClass, tables.a, tables.b, helper));
            }
            deletePrevStateLinksQuery.put(field, factory.getBuilder(XdStorageSQLBuilderName.DeleteLinksByTransaction).build(tables.b));
        }
    }

    public void markResourceAsRolledBack(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass.priority, resourceId, rolledbackResourceQuery, helper);
        command.addParameter(XdStorageCommitTransactionState.ROLLEDBACK);
        command.addParameter(Boolean.FALSE);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(cl);

        processor.pushCommand(transaction, command);
    }

    public void rollbackObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand deleteCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteByTransaction.priority, resourceId, deleteQuery, helper);
        deleteCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deleteCommand);

        final XdStorageSQLCommand selectInsertCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertFromPrevStateByTransaction.priority, resourceId, selectInsertQuery, helper);
        selectInsertCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, selectInsertCommand);

        final XdStorageSQLCommand deletePrevStateCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteByTransaction.priority, resourceId, deletePrevStateQuery, helper);
        deletePrevStateCommand.addParameter(transaction.getTransactionId());

        processor.pushCommand(transaction, deletePrevStateCommand);
    }

    public void rollbackLinks(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();
            final XdStoragePair<String, String> tables = internalFieldTables.get(field);

            final XdStorageClassInfo clInfoInternal;
            final Class<?> mapKeyClass;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
                mapKeyClass = null;
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
                mapKeyClass = classes[0];
            }

            final XdStorageSQLCommand deleteLinksCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteLinksByTransaction.priority, resourceId, deleteLinksQuery.get(field), helper);
            deleteLinksCommand.addParameter(transaction.getTransactionId());

            processor.pushCommand(transaction, deleteLinksCommand);

            final XdStorageSQLCommand selectInsertLinksCommand;
            if (clInfoInternal.getIdField() != null) {
                selectInsertLinksCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertLinksByTransaction.priority, resourceId, selectInsertLinksQuery.get(field), helper);
            } else {
                selectInsertLinksCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.SelectInsertInternalObjectsByTransaction.priority, resourceId, selectInsertLinksQuery.get(field), helper);
            }
            selectInsertLinksCommand.addParameter(transaction.getTransactionId());

            processor.pushCommand(transaction, selectInsertLinksCommand);

            final XdStorageSQLCommand deletePrevStateLinksCommand = new XdStorageSQLCommand(XdStorageSQLBuilderName.DeleteLinksByTransaction.priority, resourceId, deletePrevStateLinksQuery.get(field), helper);
            deletePrevStateLinksCommand.addParameter(transaction.getTransactionId());

            processor.pushCommand(transaction, deletePrevStateLinksCommand);
        }
    }
}
