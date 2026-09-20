package org.flib.xdstorage.sqlstorage.resource.controllers;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLCommand;
import org.flib.xdstorage.sqlstorage.sql.processor.XdStorageSQLProcessor;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class XdStorageResourcePrepareController {

    private final XdStorageServicesLocator services;

    private final XdStorageSQLResourceId resourceId;

    private final XdStorageSQLResourcesManager manager;

    private final XdStorageSQLResourceNamingService namingService;

    private final IXdStorageSQLTypesHelper helper;

    private final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields;

    private final Class<?> cl;

    private final XdStorageClassInfo clInfo;

    private final XdStorageObjectIdField idField;

    public XdStorageResourcePrepareController(final XdStorageClassInfo clInfo, final XdStorageServicesLocator services,
                                              final XdStorageSQLResourceId resourceId, final XdStorageSQLResourcesManager manager,
                                              final Map<XdStorageObjectField, Class<?>[]> internalObjectsFields) {
        this.services = services;
        this.resourceId = resourceId;
        this.manager = manager;
        this.namingService = (XdStorageSQLResourceNamingService) manager.getNamingService();
        this.helper = manager.getTypesHelper();
        this.internalObjectsFields = internalObjectsFields;
        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();
        this.idField = clInfo.getIdField();
    }

    private String transactionsTableSQL;

    private String objectTableSQL;

    private String objectPrevStateTableSQL;

    private Map<XdStorageObjectField, XdStoragePair<String, String>> internalFieldTables;

    private List<String> addForeignKeySQL;

    public void initQueries() throws XdStorageException {
        final XdStorageSQLBuilderFactory factory = XdStorageSQLBuilderFactory.getInstance();

        final String transactionsTable = namingService.getObjectTable(XdStorageTransaction.class);
        transactionsTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateTable)
                        .build(XdStorageTransaction.class, transactionsTable, helper, services.getConfiguration());

        objectTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateTable)
                        .build(clInfo, namingService.getObjectTable(cl), helper, services.getConfiguration());
        objectPrevStateTableSQL =
                factory.getBuilder(XdStorageSQLBuilderName.CreateTable)
                        .build(clInfo, namingService.getPrevStateObjectTable(cl), helper, services.getConfiguration());
        internalFieldTables = new HashMap<>();
        addForeignKeySQL = new LinkedList<>();
        for (final Map.Entry<XdStorageObjectField, Class<?>[]> entry : internalObjectsFields.entrySet()) {
            final XdStorageObjectField field = entry.getKey();
            final Class<?>[] classes = entry.getValue();

            final XdStorageClassInfo clInfoInternal;
            final Class<?> mapKeyClass;
            final Class<?> objectClass;
            if (classes.length == 1) {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[0]);
                mapKeyClass = null;
                objectClass = classes[0];
            } else {
                clInfoInternal = XdStorageObjectUtils.getClassInfo(classes[1]);
                mapKeyClass = classes[0];
                objectClass = classes[1];
            }

            final String linksTable;
            final String linksPrevStateTable;
            if (clInfoInternal.getIdField() != null) {
                final XdStorageClassInfo childClassInfo = XdStorageObjectUtils.getClassInfo(objectClass);
                final String childObjectTable = namingService.getObjectTable(objectClass);
                final String childObjectPrevStateTable = namingService.getPrevStateObjectTable(objectClass);

                addForeignKeySQL.add(factory.getBuilder(XdStorageSQLBuilderName.AddForeignKey)
                        .build(namingService.getObjectLinksTable(cl, field.getName()), field.getName() + "_" + childClassInfo.getIdField().getName(),
                                childObjectTable, childClassInfo.getIdField().getName(), helper));
                addForeignKeySQL.add(factory.getBuilder(XdStorageSQLBuilderName.AddForeignKey)
                        .build(namingService.getObjectPrevStateLinksTable(cl, field.getName()), field.getName() + "_" + childClassInfo.getIdField().getName(),
                                childObjectPrevStateTable, childClassInfo.getIdField().getName(), helper));

                linksTable = factory.getBuilder(XdStorageSQLBuilderName.CreateLinksTable)
                        .build(clInfo, namingService.getObjectLinksTable(cl, field.getName()), field.getName(), classes, helper);
                linksPrevStateTable = factory.getBuilder(XdStorageSQLBuilderName.CreateLinksTable)
                        .build(clInfo, namingService.getObjectPrevStateLinksTable(cl, field.getName()), field.getName(), classes, helper);
            } else {
                linksTable = factory.getBuilder(XdStorageSQLBuilderName.CreateInternalObjectsTable)
                        .build(clInfo, clInfoInternal, mapKeyClass, namingService.getInternalObjectsTable(cl, field.getName()), helper);
                linksPrevStateTable = factory.getBuilder(XdStorageSQLBuilderName.CreateInternalObjectsTable)
                        .build(clInfo, clInfoInternal, mapKeyClass, namingService.getInternalObjectsPrevStateTable(cl, field.getName()), helper);
            }
            internalFieldTables.put(field, new XdStoragePair<>(linksTable, linksPrevStateTable));
        }
    }

    public void createTables(final Connection connection, final XdStorageTransaction transaction) throws SQLException {
        final Statement st = connection.createStatement();
        st.execute(transactionsTableSQL);
        st.execute(objectTableSQL);
        st.execute(objectPrevStateTableSQL);

        for (final XdStoragePair<String, String> tables : internalFieldTables.values()) {
            st.execute(tables.a);
            st.execute(tables.b);
        }
    }

    public void registerForeignKeysConstrains(final XdStorageSQLProcessor processor, final XdStorageTransaction transaction) {
        for (final String sql : addForeignKeySQL)
        {
            final XdStorageSQLCommand command = new XdStorageSQLCommand(XdStorageSQLBuilderName.AddForeignKey.priority, resourceId, sql, helper, false);
            processor.pushAlterCommand(transaction, command);
        }
    }
}
