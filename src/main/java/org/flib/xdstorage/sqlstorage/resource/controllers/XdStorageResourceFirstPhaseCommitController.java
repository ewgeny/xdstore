package org.flib.xdstorage.sqlstorage.resource.controllers;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.observing.XdStorageObserverService;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLClassConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.fkresource.IXdStorageCrossDatasourceFkDaoResource;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.resource.helpers.XdStorageResourceFirstPhaseInternalObjectsHelper;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.sql.processor.*;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.*;

import java.sql.*;
import java.util.*;
import java.util.function.Function;

public class XdStorageResourceFirstPhaseCommitController {

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageTransactionResourceChanges record;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageObjectIdField idField;

    public XdStorageResourceFirstPhaseCommitController(final Class<?> cl, final XdStorageServicesLocator services,
                                                       final XdStorageSQLResourceId resourceId, final XdStorageSQLResourcesManager manager,
                                                       final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields,
                                                       final XdStorageTransactionResourceChanges record) {
        this.services = services;
        this.resourceId = resourceId;
        this.record = record;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
        this.internalObjectsFields = internalObjectsFields;
        this.cl = cl;
        this.clInfo = XdStorageObjectUtils.getClassInfo(cl);
        this.idField = clInfo.getIdField();
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
            final Class<?>[] classes = entry.getValue();

            final XdStorageClassInfo clInfoInternal;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
            }

            final String linksTable;
            final String linksPrevStateTable;
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

    private String registerResourceQuery;

    private String insertQuery;

    private String updateQuery;

    private String insertForUpdateQuery;

    private String deleteQuery;

    private String insertForDeleteQuery;

    private Map<XdStorageObjectField, XdStoragePair<String, String>> internalFieldBackupLinksQueries;

    private Map<XdStorageObjectField, String> internalFieldInsertLinksQueries;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();
        registerResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.RegisterResource).build(transactionsTable);

        insertQuery = factory.getBuilder(XdStorageSQLBuilderName.Insert).build(clInfo, objectTable, helper, services.getConfiguration());
        updateQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateById).build(clInfo, objectTable, helper);
        insertForUpdateQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertForUpdate)
                .build(clInfo, objectPrevStateTable, helper);
        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteById).build(clInfo, objectTable);
        insertForDeleteQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertForDelete)
                .build(clInfo, objectPrevStateTable, helper);

        // building links queries
        internalFieldBackupLinksQueries = new HashMap<>();
        internalFieldInsertLinksQueries = new HashMap<>();
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

            if (clInfoInternal.getIdField() != null) {
                final String selectInsertLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertLinksByOwnerId)
                        .build(clInfo, tables.a, tables.b, field.getName(), classes);
                final String deleteLinksQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteLinksByOwnerId).build(clInfo, tables.a);
                internalFieldBackupLinksQueries.put(field, new XdStoragePair<>(selectInsertLinksQuery, deleteLinksQuery));

                final String insertLinkQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertLink)
                        .build(clInfo, tables.a, field.getName(), classes);
                internalFieldInsertLinksQueries.put(field, insertLinkQuery);
            } else {
                final String selectInsertInternalObjectsQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInsertInternalObjectsByOwnerId)
                        .build(clInfo, clInfoInternal, mapKeyClass, tables.a, tables.b, helper);
                final String deleteInternalObjectsQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteInternalObjectsByOwnerId).build(clInfo, tables.a);
                internalFieldBackupLinksQueries.put(field, new XdStoragePair<>(selectInsertInternalObjectsQuery, deleteInternalObjectsQuery));

                final String insertInternalObjectQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertInternalObject)
                        .build(clInfo, clInfoInternal, mapKeyClass, tables.a, helper);
                internalFieldInsertLinksQueries.put(field, insertInternalObjectQuery);
            }
        }
    }

    private Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToInsertWithoutId;

    private Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> linksToUpdate;

    private Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> objectsToInsertWithoutIdField;

    private Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToInsert;

    private Map<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> fkToDelete;

    public void collectObjectsAndLinks(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        linksToInsertWithoutId = new HashMap<>();
        linksToUpdate = new HashMap<>();
        objectsToInsertWithoutIdField = new HashMap<>();
        fkToInsert = new HashMap<>();
        fkToDelete = new HashMap<>();
        XdStorageResourceFirstPhaseInternalObjectsHelper.collectChildrenObjectsAndLinks(transaction, resourceId, fkToInsert, fkToDelete,
                linksToInsertWithoutId, linksToUpdate, objectsToInsertWithoutIdField, record, manager);
    }

    public void registerResource(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.RegisterResource.priority, resourceId, registerResourceQuery, helper);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(transaction.getTimestart());
        command.addParameter(cl);
        command.addParameter(null);
        command.addParameter(Boolean.FALSE);
        command.addParameter(XdStorageCommitTransactionState.PREPARED);

        processor.pushCommand(transaction, command);
    }

    public void storeObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLBatch insertBatch;
        final XdStorageDefaultSQLParametersProvider insertBatchParameters;
        final XdStorageDefaultSQLParametersProviderAndIdConsumer providerAndIdConsumer;
        if (idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {

            providerAndIdConsumer = new XdStorageDefaultSQLParametersProviderAndIdConsumer(
                    (object, rs) -> {
                        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
                        final XdStorageObjectIdField idFieldSetter = clInfo.getIdField();
                        try {
                            helper.setProperty(idFieldSetter, object, rs, 1);
                        } catch (final SQLException e) {
                            throw new XdStorageRuntimeException(e);
                        }
                    });
            insertBatchParameters = providerAndIdConsumer;
            insertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.Insert.priority, resourceId, insertQuery,
                    providerAndIdConsumer, helper, providerAndIdConsumer);
        } else {

            providerAndIdConsumer = null;
            insertBatchParameters = new XdStorageDefaultSQLParametersProvider();
            insertBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.Insert.priority, resourceId, insertQuery,
                    insertBatchParameters, helper);
        }
        processor.pushCommand(transaction, insertBatch);

        final XdStorageDefaultSQLParametersProvider updateBatchParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageSQLBatch updateBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.UpdateById.priority, resourceId, updateQuery,
                updateBatchParameters, helper, null);
        processor.pushCommand(transaction, updateBatch);

        final XdStorageDefaultSQLParametersProvider insertForUpdateBatchParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageSQLBatch insertForUpdateBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertForUpdate.priority, resourceId, insertForUpdateQuery,
                insertForUpdateBatchParameters, helper, null);
        processor.pushCommand(transaction, insertForUpdateBatch);

        final XdStorageDefaultSQLParametersProvider deleteBatchParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageSQLBatch deleteBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteById.priority, resourceId, deleteQuery,
                deleteBatchParameters, helper, null);
        processor.pushCommand(transaction, deleteBatch);

        final XdStorageDefaultSQLParametersProvider insertForDeleteBatchParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageSQLBatch insertForDeleteBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertForDelete.priority, resourceId, insertForDeleteQuery,
                insertForDeleteBatchParameters, helper, null);
        processor.pushCommand(transaction, insertForDeleteBatch);

        // filling prepared statements
        for (final XdStorageObjectChange change : record.getChangesObjects()) {
            if (change.type == XdStorageObjectOperationType.Insert) {

                addInsertBatchRow(clInfo, insertBatchParameters, change, transaction, helper);
                if (idField.getIdGeneretorType() == XdStorageIdGeneratorType.DATABASE_GENERATOR) {
                    providerAndIdConsumer.addConsumer(change.newObject);
                }
            } else if (change.type == XdStorageObjectOperationType.Update) {

                addUpdateBatchRow(clInfo, updateBatchParameters, change, transaction, helper);

                addInsertForUpdateBatchRow(clInfo, insertForUpdateBatchParameters, change, transaction, helper);
            } else if (change.type == XdStorageObjectOperationType.Delete) {

                addDeleteBatchRow(clInfo, deleteBatchParameters, change);

                addInsertForDeleteBatchRow(clInfo, insertForDeleteBatchParameters, change, transaction, helper);
            }
        }
    }

    private void addInsertBatchRow(final XdStorageClassInfo clInfo, final XdStorageDefaultSQLParametersProvider provider,
                                   final XdStorageObjectChange change, final XdStorageTransaction transaction,
                                   final IXdStorageSQLTypesHelper helper) {
        final XdStorageObjectIdField idField = clInfo.getIdField();

        final XdStorageSQLConfiguration config = services.getConfiguration();
        final XdStorageSQLClassConfiguration classConfig = config.getClassConfig(clInfo.getClazz());

        final List<Object> row = provider.addRow();

        final Collection<XdStorageObjectField> fields;
        if (idField.getIdGeneretorType() != XdStorageIdGeneratorType.DATABASE_GENERATOR || classConfig.isMultiple() || classConfig.isParentDataSource()) {
            row.add(idField.get(change.newObject));
        }
        fields = clInfo.getFields().values();

        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                row.add(field.get(change.newObject));
            }
        }
        row.add(transaction.getTransactionId());
    }

    private void addUpdateBatchRow(final XdStorageClassInfo clInfo, final XdStorageDefaultSQLParametersProvider provider,
                                   final XdStorageObjectChange change, final XdStorageTransaction transaction,
                                   final IXdStorageSQLTypesHelper helper) {
        final XdStorageObjectIdField idField = clInfo.getIdField();

        List<Object> row = provider.addRow();

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                row.add(field.get(change.newObject));
            }
        }
        row.add(transaction.getTransactionId());

        row.add(idField.get(change.newObject));
    }

    private void addInsertForUpdateBatchRow(final XdStorageClassInfo clInfo, final XdStorageDefaultSQLParametersProvider provider,
                                            final XdStorageObjectChange change, final XdStorageTransaction transaction,
                                            final IXdStorageSQLTypesHelper helper) {
        final XdStorageObjectIdField idField = clInfo.getIdField();

        List<Object> row = provider.addRow();

        row.add(idField.get(change.oldObject));

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                row.add(field.get(change.oldObject));
            }
        }
        row.add(transaction.getTransactionId());
    }

    private void addDeleteBatchRow(final XdStorageClassInfo clInfo, final XdStorageDefaultSQLParametersProvider provider,
                                   final XdStorageObjectChange change) {
        final XdStorageObjectIdField idField = clInfo.getIdField();

        List<Object> row = provider.addRow();

        row.add(idField.get(change.oldObject));
    }

    private void addInsertForDeleteBatchRow(final XdStorageClassInfo clInfo, final XdStorageDefaultSQLParametersProvider provider,
                                            final XdStorageObjectChange change, final XdStorageTransaction transaction,
                                            final IXdStorageSQLTypesHelper helper) {
        final XdStorageObjectIdField idField = clInfo.getIdField();

        List<Object> row = provider.addRow();

        row.add(idField.get(change.oldObject));

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                row.add(field.get(change.oldObject));
            }
        }
        row.add(transaction.getTransactionId());
    }

    public void storeInternalObjectsAndLinks(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        // storing links
        prepareStatements(processor, transaction);

        fillLinksPreparedStatements(transaction);
        fillInternalObjectsPreparedStatements(transaction);

        observeToUnidentifiedObjects(processor, transaction);

        handleForeignKeys(transaction);
    }

    private void handleForeignKeys(final XdStorageTransaction transaction) throws XdStorageException, XdStorageConnectionException {
        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> entry : fkToInsert.entrySet()) {
            final Object parentObject = entry.getKey();
            for (final XdStoragePair<XdStorageObjectField, Object> childObject : entry.getValue()) {
                final Class<?> childCl = childObject.b.getClass();
                final XdStorageClassInfo childClassInfo = XdStorageObjectUtils.getClassInfo(childCl);
                final XdStorageObjectIdField childIdField = childClassInfo.getIdField();

                final XdStorageSQLResourceId childResourceId;
                if (childClassInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
                    childResourceId = (XdStorageSQLResourceId) manager.lockChildrenClassResource(childClassInfo, resourceId, transaction).getResourceId();
                } else {
                    childResourceId = (XdStorageSQLResourceId) manager.lockClassResource(childObject.b, childClassInfo, transaction).getResourceId();
                }

                IXdStorageCrossDatasourceFkDaoResource resource = manager.lockForeignKeyObjectsResource(resourceId, childResourceId, transaction, clInfo, childClassInfo);
                resource.insert(resourceId, parentObject, childResourceId, childObject.b, transaction);
            }
        }

        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object>>> entry : fkToDelete.entrySet()) {
            final Object parentObject = entry.getKey();
            for (final XdStoragePair<XdStorageObjectField, Object> childObject : entry.getValue()) {
                final Class<?> childCl = childObject.b.getClass();
                final XdStorageClassInfo childClassInfo = XdStorageObjectUtils.getClassInfo(childCl);
                final XdStorageObjectIdField childIdField = childClassInfo.getIdField();

                final XdStorageSQLResourceId childResourceId;
                if (childClassInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
                    childResourceId = (XdStorageSQLResourceId) manager.lockChildrenClassResource(childClassInfo, resourceId, transaction).getResourceId();
                } else {
                    childResourceId = (XdStorageSQLResourceId) manager.lockClassResource(childObject.b, childClassInfo, transaction).getResourceId();
                }

                IXdStorageCrossDatasourceFkDaoResource resource = manager.lockForeignKeyObjectsResource(resourceId, childResourceId, transaction, clInfo, childClassInfo);
                resource.delete(resourceId, parentObject, childResourceId, childObject.b, transaction);
            }
        }
    }

    private Map<XdStorageObjectField, XdStoragePair<XdStorageSQLBatch, XdStorageSQLBatch>> internalFieldBackupLinksBatches;

    private Map<XdStorageObjectField, XdStorageSQLBatch> internalFieldInsertLinksBatches;

    private void prepareStatements(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        internalFieldBackupLinksBatches = new HashMap<>();
        internalFieldInsertLinksBatches = new HashMap<>();

        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();

            final XdStorageClassInfo clInfoInternal;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
            }

            final XdStoragePair<String, String> backupQueries = internalFieldBackupLinksQueries.get(field);

            final String insertLinkQuery = internalFieldInsertLinksQueries.get(field);

            final XdStorageSQLBatch selectInsertLinksBatch;
            final XdStorageSQLBatch deleteLinksBatch;
            final XdStorageSQLBatch insertLinksBatch;
            if (clInfoInternal.getIdField() != null) {
                selectInsertLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertLinksByOwnerId.priority,
                        resourceId, backupQueries.a, new XdStorageDefaultSQLParametersProvider(), helper);
                deleteLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteLinksByOwnerId.priority,
                        resourceId, backupQueries.b, new XdStorageDefaultSQLParametersProvider(), helper);

                insertLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertLink.priority,
                        resourceId, insertLinkQuery, new XdStorageDefaultSQLParametersProvider(), helper);
            } else {
                selectInsertLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertInternalObjectsByOwnerId.priority,
                        resourceId, backupQueries.a, new XdStorageDefaultSQLParametersProvider(), helper);
                deleteLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteInternalObjectsByOwnerId.priority,
                        resourceId, backupQueries.b, new XdStorageDefaultSQLParametersProvider(), helper);

                insertLinksBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertInternalObject.priority,
                        resourceId, insertLinkQuery, new XdStorageDefaultSQLParametersProvider(), helper);
            }

            internalFieldBackupLinksBatches.put(field, new XdStoragePair<>(selectInsertLinksBatch, deleteLinksBatch));
            internalFieldInsertLinksBatches.put(field, insertLinksBatch);

            processor.pushCommand(transaction, selectInsertLinksBatch);
            processor.pushCommand(transaction, deleteLinksBatch);
            processor.pushCommand(transaction, insertLinksBatch);
        }
    }

    private void fillLinksPreparedStatements(final XdStorageTransaction transaction) {
        // filling prepared statements
        final Map<Object, Set<XdStorageObjectField>> backupedLinksObjectsIds = new HashMap<>();
        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> link : linksToUpdate.entrySet()) {
            final Object objectId = link.getKey();
            for (final XdStoragePair<XdStorageObjectField, Object[]> pair : link.getValue()) {
                final XdStorageObjectField field = pair.a;
                final Object[] values = pair.b;

                if (!backupedLinksObjectsIds.containsKey(objectId)
                        || !backupedLinksObjectsIds.get(objectId).contains(field)) {
                    final XdStoragePair<XdStorageSQLBatch, XdStorageSQLBatch> backupLinksBatches = internalFieldBackupLinksBatches.get(field);
                    addSelectInsertBackupLinksBatch(backupLinksBatches.a, objectId, transaction, helper);
                    addDeleteBackupedLinksBatch(backupLinksBatches.b, objectId, helper);

                    Set<XdStorageObjectField> fields = backupedLinksObjectsIds.get(objectId);
                    if (fields == null) {
                        backupedLinksObjectsIds.put(objectId, fields = new HashSet<>());
                    }
                    fields.add(field);
                }

                if (values != null) {
                    final XdStorageSQLBatch insertLinkBatch = internalFieldInsertLinksBatches.get(field);
                    addInsertLinkBatch(insertLinkBatch, objectId, values, transaction, helper);
                }
            }
        }
    }

    private void fillInternalObjectsPreparedStatements(final XdStorageTransaction transaction) {
        // filling prepared statements
        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> internalObject : objectsToInsertWithoutIdField.entrySet()) {
            final Object objectId = internalObject.getKey();
            for (final XdStoragePair<XdStorageObjectField, Object[]> pair : internalObject.getValue()) {
                final XdStorageObjectField field = pair.a;
                final Object[] values = pair.b;

                final XdStoragePair<XdStorageSQLBatch, XdStorageSQLBatch> backupLinksBatches = internalFieldBackupLinksBatches.get(field);
                addSelectInsertBackupInternalObjectsBatch(backupLinksBatches.a, objectId, transaction, helper);
                addDeleteBackupedInternalObjectsBatch(backupLinksBatches.b, objectId, helper);

                if (values != null) {

                    final XdStorageSQLBatch insertLinkBatch = internalFieldInsertLinksBatches.get(field);
                    addInsertInternalObjectBatch(insertLinkBatch, objectId, values, transaction, helper);
                }
            }
        }
    }

    private void observeToUnidentifiedObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        // preparing batches
        final Map<XdStorageObjectField, XdStorageSQLBatch> internalFieldInsertLinksBatches = new HashMap<>();
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();

            final String insertLinkQuery = internalFieldInsertLinksQueries.get(field);
            final XdStorageSQLBatch insertLinkBatch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertLink.priority,
                    resourceId, insertLinkQuery, new XdStorageSQLParametersProviderWithCalculationId(idField::get), helper);
            internalFieldInsertLinksBatches.put(field, insertLinkBatch);

            processor.pushCommand(transaction, insertLinkBatch);
        }

        // filling batches
        for (final Map.Entry<Object, Collection<XdStoragePair<XdStorageObjectField, Object[]>>> link : linksToInsertWithoutId.entrySet()) {
            final Object parentObject = link.getKey();
            for (final XdStoragePair<XdStorageObjectField, Object[]> pair : link.getValue()) {
                final Object[] values;
                final Object linkedObject;

                final XdStorageSQLBatch insertLinkBatch = internalFieldInsertLinksBatches.get(pair.a);
                final XdStorageSQLParametersProviderWithCalculationId provider = (XdStorageSQLParametersProviderWithCalculationId) insertLinkBatch.getParametersProvider();
                if (pair.b.length == 2) {
                    values = new Object[]{parentObject, pair.b[0], linkedObject = XdStorageObjectUtils.getWrappedObjectOrSameObject(pair.b[1])};
                    provider.addObjectIdParameterIndex(2);
                } else {
                    values = new Object[]{parentObject, linkedObject = XdStorageObjectUtils.getWrappedObjectOrSameObject(pair.b[0])};
                    provider.addObjectIdParameterIndex(1);
                }

                addInsertLinkBatch(insertLinkBatch, values, transaction, helper);

                final Class<?> clLinkedObjectClass = linkedObject.getClass();
                final XdStorageClassInfo clLinkedObjectInfo = XdStorageObjectUtils.getClassInfo(clLinkedObjectClass);
                final IXdStorageDaoResource resource;
                if (clLinkedObjectInfo.getPolicy() == XdStoragePolicy.StoreWithParentObject) {
                    resource = manager.lockChildrenClassResource(clLinkedObjectInfo, resourceId, transaction);
                } else {
                    resource = manager.lockClassResource(linkedObject, clLinkedObjectInfo, transaction);
                }

                resource.find(XdStorageObserverService.getObservableWrapper(linkedObject), transaction);
            }
        }
    }

    private static class XdStorageSQLParametersProviderWithCalculationId extends XdStorageDefaultSQLParametersProvider {

        private final List<Integer> objectIdParameterIndex = new ArrayList<>();

        private final Function<Object, Object> idProvider;

        XdStorageSQLParametersProviderWithCalculationId(final Function<Object, Object> idProvider) {
            this.idProvider = idProvider;
        }

        public void addObjectIdParameterIndex(int index) {
            objectIdParameterIndex.add(index);
        }

        @Override
        public Object getParameter(int rowIndex, int parameterIndex) {
            Object result = super.getParameter(rowIndex, parameterIndex);
            if (parameterIndex == 0) {
                result = idProvider.apply(result);
            } else if (parameterIndex == objectIdParameterIndex.get(rowIndex)) {
                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(result.getClass());
                final XdStorageObjectIdField linkIdField = clInfo.getIdField();

                result = linkIdField.get(result);
            }
            return result;
        }
    }

    private void addSelectInsertBackupLinksBatch(final XdStorageSQLBatch batch, final Object objectId,
                                                 final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(transaction.getTransactionId());
        row.add(idField.get(objectId));
    }

    private void addDeleteBackupedLinksBatch(final XdStorageSQLBatch batch, final Object objectId, final IXdStorageSQLTypesHelper helper) {
        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(idField.get(objectId));
    }

    private void addSelectInsertBackupInternalObjectsBatch(final XdStorageSQLBatch batch, final Object objectId,
                                                           final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {

        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(transaction.getTransactionId());
        row.add(idField.get(objectId));
    }

    private void addDeleteBackupedInternalObjectsBatch(final XdStorageSQLBatch batch, final Object objectId, final IXdStorageSQLTypesHelper helper) {
        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(idField.get(objectId));
    }

    private void addInsertLinkBatch(final XdStorageSQLBatch batch, final Object objectId,
                                    final Object[] values, final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        int valueIndex = 0;

        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(idField.get(objectId));
        if (values.length > 1) {
            row.add(values[valueIndex++]);
        }
        final Object linkObject = values[valueIndex];
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(linkObject.getClass());
        final XdStorageObjectIdField linkIdField = clInfo.getIdField();
        row.add(linkIdField.get(linkObject));
        row.add(transaction.getTransactionId());
    }

    private void addInsertLinkBatch(final XdStorageSQLBatch batch, final Object[] values,
                                    final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        int valueIndex = 0;

        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(values[valueIndex++]);
        if (values.length > 2) {
            row.add(values[valueIndex++]);
        }
        final Object linkObject = values[valueIndex];
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(linkObject.getClass());
        final XdStorageObjectIdField linkIdField = clInfo.getIdField();
        row.add(linkObject);
        row.add(transaction.getTransactionId());
    }

    private void addInsertInternalObjectBatch(final XdStorageSQLBatch batch, final Object objectId, final Object[] values,
                                              final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        int valueIndex = 0;

        final XdStorageDefaultSQLParametersProvider provider = (XdStorageDefaultSQLParametersProvider) batch.getParametersProvider();

        final List<Object> row = provider.addRow();

        row.add(idField.get(objectId));
        if (values.length > 1) {
            row.add(values[valueIndex++]);
        }
        final Object internalObject = values[valueIndex];
        final XdStorageClassInfo internalClassInfo = XdStorageObjectUtils.getClassInfo(internalObject.getClass());
        final Collection<XdStorageObjectField> fields = internalClassInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                row.add(field.get(internalObject));
            }
        }
        row.add(transaction.getTransactionId());
    }
}
