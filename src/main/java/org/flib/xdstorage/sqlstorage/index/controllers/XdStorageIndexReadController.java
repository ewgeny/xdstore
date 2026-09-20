package org.flib.xdstorage.sqlstorage.index.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.index.hash.XdStorageHashIndexRecord;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class XdStorageIndexReadController {

    private final DataSource dataSource;

    private final XdStorageSQLResourceId resourceId;

    private final String indexName;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageObjectIdField idField;

    private final XdStorageClassInfo indexClInfo;

    private final XdStorageObjectIdField indexIdField;

    public XdStorageIndexReadController(final XdStorageClassInfo clInfo, final DataSource dataSource,
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
        this.indexClInfo = XdStorageObjectUtils.getClassInfo(XdStorageHashIndexRecord.class);
        this.indexIdField = indexClInfo.getIdField();
    }

    String selectByIdQuery;

    String selectAllQuery;

    public void initQueries() throws XdStorageException {
        final String indexTable = namingService.getIndexTable(cl, indexName);

        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        selectByIdQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectIndexById).build(indexClInfo, indexTable);
        selectAllQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectIndex).build(indexClInfo, indexTable);
    }

    public <T> void readIndexRecordById(final Connection connection, final Object id, final List<T> records) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectByIdQuery);
        helper.setParameter(statement, 1, id);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            records.add(buildIndexRecord(result, helper));
        }
    }

    public void readIndexRecords(final Connection connection, final List<Object> records) throws SQLException {
        final PreparedStatement statement = connection.prepareStatement(selectAllQuery);

        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            records.add(buildIndexRecord(result, helper));
        }
    }

    private <T> T buildIndexRecord(final ResultSet result, final IXdStorageSQLTypesHelper helper) throws SQLException {
        final XdStorageHashIndexRecord indexRecord = new XdStorageHashIndexRecord();
        helper.setProperty(indexIdField, idField.getFieldInfo().getValueClass(), indexRecord, result, 1);

        final XdStorageSQLResourceId resourceId = new XdStorageSQLResourceId();
        final XdStorageClassInfo resourceIdClassInfo = XdStorageObjectUtils.getClassInfo(XdStorageSQLResourceId.class);
        final Map<String, XdStorageObjectField> fields = resourceIdClassInfo.getFields();
        helper.setProperty(fields.get("table"), resourceId, result, 2);
        helper.setProperty(fields.get("dataSource"), resourceId, result, 3);

        indexRecord.setResourceId(resourceId);

        return (T) indexRecord;
    }

}
