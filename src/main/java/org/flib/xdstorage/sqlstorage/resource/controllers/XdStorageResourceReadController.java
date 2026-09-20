package org.flib.xdstorage.sqlstorage.resource.controllers;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.resource.IXdStorageDaoResource;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.*;

import java.lang.reflect.Array;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class XdStorageResourceReadController {

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageObjectIdField idField;

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields;

    public XdStorageResourceReadController(final Class<?> cl, final XdStorageSQLResourceId resourceId, final XdStorageSQLResourcesManager manager,
                                           final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields) {
        this.resourceId = resourceId;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();

        this.cl = cl;
        this.clInfo = XdStorageObjectUtils.getClassInfo(cl);
        this.idField = clInfo.getIdField();

        this.internalObjectsFields = internalObjectsFields;
    }

    private String selectQuery;

    private String selectByIdQuery;

    private Map<XdStorageObjectField, String> internalFieldQueries;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String objectTable = namingService.getObjectTable(cl);

        selectQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectAll).build(objectTable);
        selectByIdQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectById).build(clInfo, objectTable);

        internalFieldQueries = new HashMap<>();
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();

            final XdStorageClassInfo clInfoInternal;
            final Class<?> mapKeyClass;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
                mapKeyClass = null;
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
                mapKeyClass = classes[0];
            }

            final String linksTable, linksQuery;
            if (clInfoInternal.getIdField() != null) {
                linksTable = namingService.getObjectLinksTable(cl, field.getName());
                linksQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectLinks).build(clInfo, linksTable);
            } else {
                linksTable = namingService.getInternalObjectsTable(cl, field.getName());
                linksQuery = factory.getBuilder(XdStorageSQLBuilderName.SelectInternalObjects).build(clInfo, clInfoInternal, mapKeyClass, linksTable, helper);
            }
            internalFieldQueries.put(field, linksQuery);
        }

    }

    public <T> void readObjects(final Connection connection, final Collection<T> objects) throws SQLException, InstantiationException, IllegalAccessException {
        final Statement statement = connection.createStatement();
        final ResultSet rs = statement.executeQuery(selectQuery);
        while (rs.next()) {
            objects.add(buildObject(clInfo, cl, rs, helper));
        }
    }

    public <T> void readObjectById(final Connection connection, final Object objectId, final Collection<T> objects) throws SQLException, InstantiationException, IllegalAccessException {
        final PreparedStatement statement = connection.prepareStatement(selectByIdQuery);
        helper.setParameter(statement, 1, objectId);
        final ResultSet result = statement.executeQuery();
        while (result.next()) {
            objects.add(buildObject(clInfo, cl, result, helper));
        }
    }

    private <T> T buildObject(final XdStorageClassInfo clInfo, final Class<?> cl, final ResultSet row, final IXdStorageSQLTypesHelper helper)
            throws IllegalAccessException, InstantiationException, SQLException {
        final T object = (T) cl.newInstance();

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                helper.setProperty(field, object, row);
            }
        }

        return object;
    }

    public <T> void readLinks(final Connection connection, final Collection<T> objects) throws SQLException, InstantiationException, IllegalAccessException {
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();

            final XdStorageClassInfo clInfoInternal;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
            }

            final XdStorageObjectIdField propertyIdField = clInfoInternal.getIdField();

            final String query = internalFieldQueries.get(field);
            for (final Object object : objects) {
                final Object objectId = idField.get(object);

                final PreparedStatement linkStatement = connection.prepareStatement(query);
                helper.setParameter(linkStatement, 1, objectId);
                final ResultSet linksRs = linkStatement.executeQuery();

                if (propertyIdField != null) {
                    buildObjectLinks(object, field, linksRs, helper);
                } else {
                    buildInternalObjects(object, field, linksRs, helper);
                }
            }
        }
    }

    private void buildObjectLinks(final Object object, final XdStorageObjectField field, final ResultSet rs, final IXdStorageSQLTypesHelper helper)
            throws SQLException, IllegalAccessException, InstantiationException {
        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        Collection<Object> collection = null;
        Map<Object, Object> map = null;
        if (fieldInfo.isArray()) {
            collection = new ArrayList<>();
        } else if (fieldInfo.isCollection()) {
            collection = new ArrayList<>();
        } else if (fieldInfo.isMap()) {
            map = new HashMap<>();
        }

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(fieldInfo.getValueClass());
        final XdStorageObjectIdField propertyIdField = clInfo.getIdField();

        final Class<?> propertyClass = fieldInfo.getValueClass();
        final Class<?> keyClass = fieldInfo.getMapKeyClass();
        while (rs.next()) {
            final Object key = keyClass != null ? helper.getObject(keyClass, rs, 2) : null;

            final Object link = propertyClass.newInstance();
            helper.setProperty(propertyIdField, link, rs, keyClass != null ? 3 : 2);

            if (fieldInfo.isArray() || fieldInfo.isCollection()) {
                collection.add(link);
            } else if (fieldInfo.isMap()) {
                map.put(key, link);
            } else {
                field.set(object, link);
            }
        }
        if (fieldInfo.isArray()) {
            field.set(object, collection.toArray((Object[]) Array.newInstance(propertyClass, collection.size())));
        } else if (fieldInfo.isCollection()) {
            field.set(object, collection);
        } else if (fieldInfo.isMap()) {
            field.set(object, map);
        }
    }

    private void buildInternalObjects(final Object object, final XdStorageObjectField field, final ResultSet rs, final IXdStorageSQLTypesHelper helper)
            throws SQLException, IllegalAccessException, InstantiationException {
        final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

        final Class<?> propertyClass = fieldInfo.getValueClass();
        final Class<?> keyClass = fieldInfo.getMapKeyClass();

        Collection<Object> collection = null;
        Map<Object, Object> map = null;
        if (fieldInfo.isArray()) {
            collection = new ArrayList<>();
        } else if (fieldInfo.isCollection()) {
            collection = new ArrayList<>();
        } else if (fieldInfo.isMap()) {
            map = new HashMap<>();
        }

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);

        while (rs.next()) {
            final Object key = keyClass != null ? helper.getObject(keyClass, rs, 2) : null;

            final Object internalObject = fillInternalObject(propertyClass.newInstance(), rs, clInfo, helper);

            if (fieldInfo.isArray() || fieldInfo.isCollection()) {
                collection.add(internalObject);
            } else if (fieldInfo.isMap()) {
                map.put(key, internalObject);
            } else {
                field.set(object, internalObject);
            }
        }
        if (fieldInfo.isArray()) {
            field.set(object, collection.toArray((Object[]) Array.newInstance(propertyClass, collection.size())));
        } else if (fieldInfo.isCollection()) {
            field.set(object, collection);
        } else if (fieldInfo.isMap()) {
            field.set(object, map);
        }
    }

    private Object fillInternalObject(final Object object, final ResultSet rs, final XdStorageClassInfo clInfo, final IXdStorageSQLTypesHelper helper) throws SQLException {
        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (helper.isSimpleType(field)) {
                helper.setProperty(field, object, rs);
            }
        }
        return object;
    }

    public <T> void loadInternalObjectsByLinks(final Collection<T> objects, final XdStorageTransaction transaction)
            throws XdStorageException, XdStorageConnectionException {
        for (final XdStorageObjectField field : internalObjectsFields.keySet()) {
            final XdStorageObjectFieldInfo fieldInfo = field.getFieldInfo();

            final Class<?> propertyClass = fieldInfo.getValueClass();

            final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(propertyClass);
            final XdStoragePolicy policy = clInfo.getPolicy();
            if (policy != XdStoragePolicy.StoreWithParentObject || clInfo.getIdField() == null) {
                continue;
            }

            final IXdStorageDaoResource resource = manager.lockChildrenClassResource(clInfo, resourceId, transaction);
            resource.read(transaction);

            for (final Object object : objects) {
                final Object value = field.get(object);
                if (value == null) {
                    continue;
                }

                if (fieldInfo.isArray()) {
                    for (final Object reference : (Object[]) value) {
                        resource.readByReference(reference, transaction);
                    }
                } else if (fieldInfo.isCollection()) {
                    for (final Object reference : (Collection<?>) value) {
                        resource.readByReference(reference, transaction);
                    }
                } else if (fieldInfo.isMap()) {
                    for (final Object reference : ((Map<?, ?>) value).values()) {
                        resource.readByReference(reference, transaction);
                    }
                } else {
                    resource.readByReference(value, transaction);
                }
            }
        }
    }
}
