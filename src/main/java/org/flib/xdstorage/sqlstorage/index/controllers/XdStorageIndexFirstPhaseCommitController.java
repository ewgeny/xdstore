package org.flib.xdstorage.sqlstorage.index.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageDefaultSQLParametersProvider;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLBatch;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageCommitTransactionState;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.*;
import java.util.function.Function;

public class XdStorageIndexFirstPhaseCommitController {

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageTransactionResourceChanges record;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo indexClInfo;

    private final XdStorageObjectIdField indexIdField;

    public XdStorageIndexFirstPhaseCommitController(final XdStorageClassInfo clInfo,
                                                    final XdStorageSQLResourceId resourceId, final String indexName,
                                                    final XdStorageSQLResourcesManager manager,
                                                    final XdStorageTransactionResourceChanges record) {
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.record = record;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
        this.cl = clInfo.getClazz();
        this.indexClInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
        this.indexIdField = indexClInfo.getIdField();
    }

    private String registerResourceQuery;

    private String insertQuery;

    private String updateQuery;

    private String insertForUpdateQuery;

    private String deleteQuery;

    private String insertForDeleteQuery;

    public void initQueries() throws XdStorageException {
        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        final String indexTable = namingService.getIndexTable(cl, indexName);
        final String indexPrevStateTable = namingService.getPrevStateIndexTable(cl, indexName);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();
        registerResourceQuery = factory.getBuilder(XdStorageSQLBuilderName.RegisterResource).build(transactionsTable);

        insertQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertIndex).build(indexClInfo, indexTable);
        updateQuery = factory.getBuilder(XdStorageSQLBuilderName.UpdateIndexById).build(indexClInfo, indexTable);
        insertForUpdateQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertIndexForUpdate)
                .build(indexClInfo, indexPrevStateTable);
        deleteQuery = factory.getBuilder(XdStorageSQLBuilderName.DeleteIndexById).build(indexClInfo, indexTable);
        insertForDeleteQuery = factory.getBuilder(XdStorageSQLBuilderName.InsertIndexForDelete)
                .build(indexClInfo, indexPrevStateTable);
    }

    public void registerResource(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.RegisterResource.priority, resourceId, registerResourceQuery, helper);
        command.addParameter(transaction.getTransactionId());
        command.addParameter(transaction.getTimestart());
        command.addParameter(cl);
        command.addParameter(XdStorageHashIndexRecord.class);
        command.addParameter(Boolean.FALSE);
        command.addParameter(XdStorageCommitTransactionState.PREPARED);

        processor.pushCommand(transaction, command);
    }

    private Map<Object, IXdStorageIdObservableWrapper> unindentifiedRecords;

    public void storeIndexRecords(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        unindentifiedRecords = new HashMap<>();

        final XdStorageDefaultSQLParametersProvider insertParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageDefaultSQLParametersProvider updateParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageDefaultSQLParametersProvider insertForUpdateParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageDefaultSQLParametersProvider deleteParameters = new XdStorageDefaultSQLParametersProvider();
        final XdStorageDefaultSQLParametersProvider insertForDeleteParameters = new XdStorageDefaultSQLParametersProvider();

        // filling prepared statements
        for (final XdStorageObjectChange change : record.getChangesObjects()) {
            if (change.type == XdStorageObjectOperationType.Insert) {
                final Object object = change.newObject;
                if (IXdStorageIdObservableWrapper.class.isAssignableFrom(object.getClass())) {
                    unindentifiedRecords.putIfAbsent(object, (IXdStorageIdObservableWrapper) object);
                } else {
                    fillInsertStatement(insertParameters, object, transaction, helper);
                }
            } else if (change.type == XdStorageObjectOperationType.Update) {
                fillUpdateStatement(updateParameters, change.newObject, transaction, helper);
                fillInsertStatement(insertForUpdateParameters, change.newObject, transaction, helper);
            } else if (change.type == XdStorageObjectOperationType.Delete) {
                fillDeleteStatement(deleteParameters, change.oldObject, transaction, helper);
                fillInsertStatement(insertForDeleteParameters, change.oldObject, transaction, helper);
            }
        }

        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertIndex.priority, resourceId, insertQuery, insertParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.UpdateIndexById.priority, resourceId, updateQuery, updateParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertIndexForUpdate.priority, resourceId, insertForUpdateQuery, insertForUpdateParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.DeleteIndexById.priority, resourceId, deleteQuery, deleteParameters, helper));
        processor.pushCommand(transaction, new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertIndexForDelete.priority, resourceId, insertForDeleteQuery, insertForDeleteParameters, helper));

        observeToUnidentifiedObjects(processor, transaction);
    }

    private void observeToUnidentifiedObjects(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        if (unindentifiedRecords.isEmpty()) {
            return;
        }

        final XdStorageSQLParametersProviderWithCalculationId parameters = new XdStorageSQLParametersProviderWithCalculationId(indexIdField::get);
        for (Object object : unindentifiedRecords.values()) {
            fillInsertStatement(parameters, object, transaction, helper);
        }

        final XdStorageSQLBatch batch = new XdStorageSQLBatch(XdStorageSQLBuilderName.InsertIndex.priority, resourceId, insertQuery, parameters, helper);
        processor.pushCommand(transaction, batch);
    }

    private static class XdStorageSQLParametersProviderWithCalculationId extends XdStorageDefaultSQLParametersProvider {

        private final Function<Object, Object> idProvider;

        XdStorageSQLParametersProviderWithCalculationId(final Function<Object, Object> idProvider) {
            this.idProvider = idProvider;
        }

        @Override
        public Object getParameter(int rowIndex, int parameterIndex) {
            Object result = super.getParameter(rowIndex, parameterIndex);
            if (parameterIndex == 0) {
                result = idProvider.apply(result);
            }
            return result;
        }
    }

    private void fillInsertStatement(final XdStorageDefaultSQLParametersProvider parameters, final Object changedObject,
                                     final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {

        final List<Object> row = parameters.addRow();

        final XdStorageHashIndexRecord object = cast(changedObject);
        row.add(indexIdField.get(object));

        final XdStorageSQLResourceId objectResourceId = cast(object.getResourceId());
        row.add(objectResourceId.getTable());
        row.add(objectResourceId.getDataSource());

        row.add(transaction.getTransactionId());
    }

    private void fillUpdateStatement(final XdStorageDefaultSQLParametersProvider parameters, final Object changedObject,
                                     final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        final List<Object> row = parameters.addRow();

        final XdStorageHashIndexRecord object = cast(changedObject);

        final XdStorageSQLResourceId objectResourceId = cast(object.getResourceId());
        row.add(objectResourceId.getTable());
        row.add(objectResourceId.getDataSource());

        row.add(transaction.getTransactionId());

        row.add(indexIdField.get(object));
    }

    private void fillDeleteStatement(final XdStorageDefaultSQLParametersProvider parameters, final Object changedObject,
                                     final XdStorageTransaction transaction, final IXdStorageSQLTypesHelper helper) {
        final List<Object> row = parameters.addRow();

        final XdStorageHashIndexRecord object = cast(changedObject);
        row.add(indexIdField.get(object));
    }

    protected static <T> T cast(final Object value) {
        try {
            return (T) value;
        } catch (final Throwable t) {
            throw new XdStorageRuntimeException(t);
        }
    }
}
