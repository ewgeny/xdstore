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
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XdStorageSearchIndexReadController {

    private final DataSource dataSource;

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageObjectIdField idField;

    public XdStorageSearchIndexReadController(final XdStorageClassInfo clInfo, final DataSource dataSource,
                                              final XdStorageSQLResourceId resourceId, final String indexName,
                                              final XdStorageSQLResourcesManager manager) {
        this.dataSource = dataSource;
        this.resourceId = resourceId;
        this.indexName = indexName;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();
        this.idField = clInfo.getIdField();
    }

    private String selectReferencesSQL;

    private String selectFieldsSQL;

    private String selectChildrenFieldsSQL;

    private String selectAllReferencesSQL;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String table = namingService.getSearchIndexTable(cl, indexName);

        selectReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_REFERENCES, table, clInfo);

        selectFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_FIELDS, table, clInfo);

        selectChildrenFieldsSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectSearchIndexByObjectId)
                        .build(XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS, table, clInfo);

        selectAllReferencesSQL =
                factory.getBuilder(XdStorageSQLBuilderName.SelectIndex).build(clInfo, table);
    }

    public void readIndexRecordById(final Connection connection, final Object id, final List<XdStorageSearchIndexRecord> records) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectReferencesSQL);
        helper.setParameter(statement, 1, id);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            records.add(buildIndexRecord(id, result, connection, helper));
        }
    }

    private XdStorageSearchIndexRecord buildIndexRecord(final Object id, final ResultSet result, final Connection connection,
                                                        final IXdStorageSQLTypesHelper helper) throws SQLException {
        final XdStorageSearchIndexRecord record = new XdStorageSearchIndexRecord();

        record.setId(id);

        XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        resourceId.setTable((String) helper.getObject(String.class, result, 2));
        resourceId.setDataSource((String) helper.getObject(String.class, result, 3));

        record.setResourceId(resourceId);

        Map<String, Object> fieldValues = new HashMap<>();
        readFieldValues(connection, id, helper, fieldValues);
        record.setFieldValues(fieldValues);

        Map<Class<?>, Map<Object, Map<String, Map<String, Object>>>> childrenFieldValues = new HashMap<>();
        readChildrenFieldValues(connection, id, helper, record);

        return record;
    }

    private void readFieldValues(final Connection connection, final Object id, final IXdStorageSQLTypesHelper helper,
                                 final Map<String, Object> fieldValues) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectFieldsSQL);
        helper.setParameter(statement, 1, id);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            final String fieldName = (String) helper.getObject(String.class, result, 2);
            final Class<?> valueClass = (Class<?>) helper.getObject(Class.class, result, 3);
            if(valueClass == null) {
                fieldValues.put(fieldName, null);
            } else {
                final String fieldValue = (String) helper.getObject(String.class, result, 4);
                fieldValues.put(fieldName, helper.simpleTypeValueFromString(valueClass, fieldValue));
            }
        }
    }

    private void readChildrenFieldValues(final Connection connection, final Object id, final IXdStorageSQLTypesHelper helper,
                                         final XdStorageSearchIndexRecord record) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectChildrenFieldsSQL);
        helper.setParameter(statement, 1, id);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            final Class<?> childClass = (Class<?>) helper.getObject(Class.class, result, 2);
            final String childFieldName = (String) helper.getObject(String.class, result, 3);

            final Class<?> childIdClass = (Class<?>) helper.getObject(Class.class, result, 4);
            final Object childId = helper.simpleTypeValueFromString(childIdClass, (String) helper.getObject(String.class, result, 5));

            final String fieldName = (String) helper.getObject(String.class, result, 6);
            final Class<?> valueClass = (Class<?>) helper.getObject(Class.class, result, 7);
            final Object value;
            if(valueClass == null) {
                value = null;
            } else {
                value = helper.simpleTypeValueFromString(valueClass, (String) helper.getObject(String.class, result, 8));
            }

            record.addChildFieldValue(childFieldName, childClass, childId, fieldName, value);
        }
    }

    public void readIndexRecords(final Connection connection, final List<Object> records) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectAllReferencesSQL);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            final Object id = helper.getObject(idField.getFieldInfo().getValueClass(), result, 1);
            records.add(buildIndexRecord(id, result, connection, helper));
        }
    }
}
