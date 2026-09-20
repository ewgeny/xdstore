package org.flib.xdstorage.sqlstorage.search.controllers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageDefaultSQLParametersProvider;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLBatch;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XdStorageSearchIndexFirstPhaseCommitController {

    private static final Logger log = LogManager.getLogger(XdStorageSearchIndexFirstPhaseCommitController.class);

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageTransactionResourceChanges record;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    public XdStorageSearchIndexFirstPhaseCommitController(final XdStorageClassInfo clInfo,
                                                          final XdStorageSQLResourceId resourceId, final String indexName,
                                                          final XdStorageSQLResourcesManager manager,
                                                          final XdStorageTransactionResourceChanges record) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.record = record;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();
    }

    private String registerResourceQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();
        registerResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.RegisterResource).build(transactionsTable);

        final String table = namingService.getSearchIndexTable(cl, indexName);
        final String prevStateTable = namingService.getPrevStateSearchIndexTable(cl, indexName);

        initInsertQueries(factory, table);
        initDeleteQueries(factory, table);
        initSelectInsertQueries(factory, table, prevStateTable);
    }

    private String insertReferencesSQL;

    private String insertFieldsSQL;

    private String insertChildrenFieldsSQL;

    private void initInsertQueries(final XdStorageSQLBuilderFactory factory, final String table) throws XdStorageException {
        insertReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.InsertSearchIndex)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, clInfo);

        insertFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.InsertSearchIndex)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, clInfo);

        insertChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.InsertSearchIndex)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, clInfo);
    }

    private String deleteReferencesSQL;

    private String deleteFieldsSQL;

    private String deleteChildrenFieldsSQL;

    private void initDeleteQueries(final XdStorageSQLBuilderFactory factory, final String table) throws XdStorageException {
        deleteReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, clInfo);

        deleteFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, clInfo);

        deleteChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, clInfo);
    }

    private String selectInsertReferencesSQL;

    private String selectInsertFieldsSQL;

    private String selectInsertChildrenFieldsSQL;

    private void initSelectInsertQueries(final XdStorageSQLBuilderFactory factory, final String table, final String prevStateTable) throws XdStorageException {
        selectInsertReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, prevStateTable, clInfo);

        selectInsertFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, prevStateTable, clInfo);

        selectInsertChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, prevStateTable, clInfo);
    }

    public void registerResource(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.RegisterResource.priority, resourceId, registerResourceQuery, helper);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(transaction.getTimestart());
        command.addParameter(cl);
        command.addParameter(XdStorageSearchIndexRecord.class);
        command.addParameter(Boolean.FALSE);
        command.addParameter(XdStorageCommitTransactionState.PREPARED);

        processor.pushCommand(transaction, command);
    }

    private final XdStorageDefaultSQLParametersProvider insertReferencesParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider insertFieldsParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider insertChildrenFieldsParameters = new XdStorageDefaultSQLParametersProvider();


    private final XdStorageDefaultSQLParametersProvider deleteReferencesParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider deleteFieldsParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider deleteChildrenFieldsParameters = new XdStorageDefaultSQLParametersProvider();


    private final XdStorageDefaultSQLParametersProvider selectInsertReferencesParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider selectInsertFieldsParameters = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider selectInsertChieldrenFieldsParameters = new XdStorageDefaultSQLParametersProvider();

    /**
     * Logic:
     * 1) move records for update to prev state sidx table
     * 2) delete records for update from sidx table
     * 3) mode records for deletion to prev state table
     * 4) delete records for deletion from sidx table
     * 5) insert new index records
     * 6) insert records for updatable index records
     *
     * @param processor
     * @param transaction
     */
    public void storeIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageClassInfo searchIndexRecordClInfo = XdStorageObjectUtils.getClassInfo(XdStorageSearchIndexRecord.class);

        for(XdStorageObjectChange change : record.getChangesObjects()) {
            if (change.type == XdStorageObjectOperationType.Insert) {
                if (change.newObject instanceof IXdStorageIdObservableWrapper) {
                    registerSearchIndexRecord((IXdStorageIdObservableWrapper)change.newObject);
                } else {
                    fillInsertStatement(change.newObject, transaction, helper);
                }
            } else if (change.type == XdStorageObjectOperationType.Update) {
                fillSelectInsertStatement(searchIndexRecordClInfo, change.oldObject, transaction, helper);
                fillDeleteStatement(searchIndexRecordClInfo, change.oldObject, transaction, helper);
                if (change.newObject instanceof IXdStorageIdObservableWrapper) {
                    registerSearchIndexRecord((IXdStorageIdObservableWrapper)change.newObject);
                } else {
                    fillInsertStatement(change.newObject, transaction, helper);
                }
            } else if (change.type == XdStorageObjectOperationType.Delete) {
                fillSelectInsertStatement(searchIndexRecordClInfo, change.oldObject, transaction, helper);
                fillDeleteStatement(searchIndexRecordClInfo, change.oldObject, transaction, helper);
            }
        }

        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId.priority, resourceId, selectInsertReferencesSQL, selectInsertReferencesParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId.priority, resourceId, selectInsertFieldsSQL, selectInsertFieldsParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId.priority, resourceId, selectInsertChildrenFieldsSQL, selectInsertChieldrenFieldsParameters, helper));

        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId.priority, resourceId, deleteReferencesSQL, deleteReferencesParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId.priority, resourceId, deleteFieldsSQL, deleteFieldsParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId.priority, resourceId, deleteChildrenFieldsSQL, deleteChildrenFieldsParameters, helper));

        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertReferencesSQL, insertReferencesParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertFieldsSQL, insertFieldsParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertChildrenFieldsSQL, insertChildrenFieldsParameters, helper));

        observeToUnidentifiedObjects(processor, transaction);
    }

    private void fillInsertStatement(final Object object, final XdStorageTransaction transaction,
                                     final IXdStorageSQLTypesHelper helper) {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;
        final XdStorageSQLResourceId resourceId = (XdStorageSQLResourceId) record.getResourceId();

        final List<Object> referencesRow = insertReferencesParameters.addRow();
        referencesRow.add(record.getId());
        referencesRow.add(resourceId.getTable());
        referencesRow.add(resourceId.getDataSource());
        referencesRow.add(transaction.getTransactionId());

        for(final Map.Entry<String, Object> entry : record.getFieldValues().entrySet()) {
            final List<Object> fieldsRow = insertFieldsParameters.addRow();

            fieldsRow.add(record.getId());
            fieldsRow.add(entry.getKey());
            final Object value = entry.getValue();
            if(value == null) {
                fieldsRow.add(null);
                fieldsRow.add(null);
            } else {
                fieldsRow.add(value.getClass());
                fieldsRow.add(helper.simpleTypeValueToString(value));
            }
            fieldsRow.add(transaction.getTransactionId());
        }

        Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldsByClassIdFieldName = record.getChildrenFieldValues();
        if(childrenFieldsByClassIdFieldName != null) {
            for (final Map.Entry<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldsByClassIdFieldNameEntry : childrenFieldsByClassIdFieldName.entrySet()) {
                final Class<?> childrenClass = childrenFieldsByClassIdFieldNameEntry.getKey();
                final Map<Object, Map<String, Map<String, Object>>> childrenFieldsByIdFieldName = childrenFieldsByClassIdFieldNameEntry.getValue();

                for (final Map.Entry<Object, Map<String, Map<String, Object>>> childrenFieldsByIdFieldNameEntry : childrenFieldsByIdFieldName.entrySet()) {
                    final Object childId = childrenFieldsByIdFieldNameEntry.getKey();
                    final Map<String, Map<String, Object>> childrenFieldsByFieldName = childrenFieldsByIdFieldNameEntry.getValue();

                    for (final Map.Entry<String, Map<String, Object>> childrenFieldsByFieldNameEntry : childrenFieldsByFieldName.entrySet()) {
                        final String childFieldName = childrenFieldsByFieldNameEntry.getKey();
                        final Map<String, Object> fieldValues = childrenFieldsByFieldNameEntry.getValue();

                        for (final Map.Entry<String, Object> valueEntry : fieldValues.entrySet()) {
                            final List<Object> childrenFieldsRow = insertChildrenFieldsParameters.addRow();

                            childrenFieldsRow.add(record.getId());
                            childrenFieldsRow.add(childrenClass);
                            childrenFieldsRow.add(childFieldName);
                            childrenFieldsRow.add(childId.getClass());
                            childrenFieldsRow.add(helper.simpleTypeValueToString(childId));
                            childrenFieldsRow.add(valueEntry.getKey());
                            final Object value = valueEntry.getValue();
                            if (value == null) {
                                childrenFieldsRow.add(null);
                                childrenFieldsRow.add(null);
                            } else {
                                childrenFieldsRow.add(value.getClass());
                                childrenFieldsRow.add(helper.simpleTypeValueToString(value));
                            }
                            childrenFieldsRow.add(transaction.getTransactionId());
                        }
                    }
                }
            }
        }
    }

    private void fillSelectInsertStatement(final XdStorageClassInfo searchIndexRecordClInfo, final Object object, final XdStorageTransaction transaction,
                                           final IXdStorageSQLTypesHelper helper) {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final List<Object> selectInsertReferencesRow = selectInsertReferencesParameters.addRow();
        selectInsertReferencesRow.add(transaction.getTransactionId());
        selectInsertReferencesRow.add(record.getId());

        final List<Object> selectInsertFieldsRow = selectInsertFieldsParameters.addRow();
        selectInsertFieldsRow.add(transaction.getTransactionId());
        selectInsertFieldsRow.add(record.getId());

        final List<Object> selectInsertChildrenFieldsRow = selectInsertChieldrenFieldsParameters.addRow();
        selectInsertChildrenFieldsRow.add(transaction.getTransactionId());
        selectInsertChildrenFieldsRow.add(record.getId());
    }

    private void fillDeleteStatement(final XdStorageClassInfo searchIndexRecordClInfo, final Object object, final XdStorageTransaction transaction,
                                     final IXdStorageSQLTypesHelper helper) {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;

        final List<Object> deleteReferencesRow = deleteReferencesParameters.addRow();
        deleteReferencesRow.add(record.getId());

        final List<Object> deleteFieldsRow = deleteFieldsParameters.addRow();
        deleteFieldsRow.add(record.getId());

        final List<Object> deleteChildrenFieldsRow = deleteChildrenFieldsParameters.addRow();
        deleteChildrenFieldsRow.add(record.getId());
    }

    private Map<Object, IXdStorageIdObservableWrapper> toInsert = new HashMap<>();

    private void registerSearchIndexRecord(final IXdStorageIdObservableWrapper wrapper) {
        toInsert.put(wrapper, wrapper);
    }

    private final XdStorageDefaultSQLParametersProvider insertReferencesParametersObs = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider insertFieldsParametersObs = new XdStorageDefaultSQLParametersProvider();
    private final XdStorageDefaultSQLParametersProvider insertChildrenFieldsParametersObs = new XdStorageDefaultSQLParametersProvider();

    private void observeToUnidentifiedObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        if (toInsert.isEmpty()) {
            return;
        }

        for (Object record : toInsert.values()) {
            fillInsertStatementObs(record, transaction, helper);
        }

        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertReferencesSQL, insertReferencesParametersObs, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertFieldsSQL, insertFieldsParametersObs, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertSearchIndex.priority, resourceId, insertChildrenFieldsSQL, insertChildrenFieldsParametersObs, helper));
    }

    private void fillInsertStatementObs(final Object object, final XdStorageTransaction transaction,
                                     final IXdStorageSQLTypesHelper helper) {
        final XdStorageSearchIndexRecord record = (XdStorageSearchIndexRecord) object;
        final XdStorageSQLResourceId resourceId = (XdStorageSQLResourceId) record.getResourceId();

        final List<Object> referencesRow = insertReferencesParametersObs.addRow();
        referencesRow.add(record.getId());
        referencesRow.add(resourceId.getTable());
        referencesRow.add(resourceId.getDataSource());
        referencesRow.add(transaction.getTransactionId());

        for(final Map.Entry<String, Object> entry : record.getFieldValues().entrySet()) {
            final List<Object> fieldsRow = insertFieldsParametersObs.addRow();

            fieldsRow.add(record.getId());
            fieldsRow.add(entry.getKey());
            final Object value = entry.getValue();
            if(value == null) {
                fieldsRow.add(null);
                fieldsRow.add(null);
            } else {
                fieldsRow.add(value.getClass());
                fieldsRow.add(helper.simpleTypeValueToString(value));
            }
            fieldsRow.add(transaction.getTransactionId());
        }

        Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldsByClassIdFieldName = record.getChildrenFieldValues();
        if(childrenFieldsByClassIdFieldName != null) {
            for (final Map.Entry<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldsByClassIdFieldNameEntry : childrenFieldsByClassIdFieldName.entrySet()) {
                final Class<?> childrenClass = childrenFieldsByClassIdFieldNameEntry.getKey();
                final Map<Object, Map<String, Map<String, Object>>> childrenFieldsByIdFieldName = childrenFieldsByClassIdFieldNameEntry.getValue();

                for (final Map.Entry<Object, Map<String, Map<String, Object>>> childrenFieldsByIdFieldNameEntry : childrenFieldsByIdFieldName.entrySet()) {
                    final Object childId = childrenFieldsByIdFieldNameEntry.getKey();
                    final Map<String, Map<String, Object>> childrenFieldsByFieldName = childrenFieldsByIdFieldNameEntry.getValue();

                    for (final Map.Entry<String, Map<String, Object>> childrenFieldsByFieldNameEntry : childrenFieldsByFieldName.entrySet()) {
                        final String childFieldName = childrenFieldsByFieldNameEntry.getKey();
                        final Map<String, Object> fieldValues = childrenFieldsByFieldNameEntry.getValue();

                        for (final Map.Entry<String, Object> valueEntry : fieldValues.entrySet()) {
                            final List<Object> childrenFieldsRow = insertChildrenFieldsParametersObs.addRow();

                            childrenFieldsRow.add(record.getId());
                            childrenFieldsRow.add(childrenClass);
                            childrenFieldsRow.add(childFieldName);
                            childrenFieldsRow.add(childId.getClass());
                            childrenFieldsRow.add(helper.simpleTypeValueToString(childId));
                            childrenFieldsRow.add(valueEntry.getKey());
                            final Object value = valueEntry.getValue();
                            if (value == null) {
                                childrenFieldsRow.add(null);
                                childrenFieldsRow.add(null);
                            } else {
                                childrenFieldsRow.add(value.getClass());
                                childrenFieldsRow.add(helper.simpleTypeValueToString(value));
                            }
                            childrenFieldsRow.add(transaction.getTransactionId());
                        }
                    }
                }
            }
        }
    }
}
