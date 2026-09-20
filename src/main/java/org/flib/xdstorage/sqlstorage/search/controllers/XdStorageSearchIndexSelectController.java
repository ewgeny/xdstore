package org.flib.xdstorage.sqlstorage.search.controllers;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.IXdStorageSimpleWrapper;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.factories.IXdStorageReferenceProvider;
import org.flib.xdstorage.search.query.XdStorageSearchQuery;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourcesManager;
import org.flib.xdstorage.sqlstorage.search.query.XdStorageSqlSearchQuery;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderFactory;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

public class XdStorageSearchIndexSelectController {

    private final XdStorageClassInfo clInfo;

    private final Class<?> cl;

    private final XdStorageObjectIdField idField;

    private final Class<?> idFieldClass;

    private final String indexName;

    private final IXdStorageSQLTypesHelper helper;

    private final IXdStorageReferenceProvider referencesProvider;

    private final IXdStorage storage;

    private final XdStorageSQLResourceNamingService namingService;

    public XdStorageSearchIndexSelectController(final XdStorageClassInfo clInfo, final String indexName,
                                                final XdStorageSQLResourcesManager manager, XdStorageServicesLocator services) {
        this.clInfo = clInfo;
        this.cl = clInfo.getClazz();
        this.indexName = indexName;
        this.referencesProvider = services.getReferencesProvider();
        this.storage = services.getStorage();
        this.helper = services.getTypesHelper();
        this.idField = clInfo.getIdField();
        this.idFieldClass = idField.field.getType();
        this.namingService = (XdStorageSQLResourceNamingService) services.getNamingService();
    }

    private String selectQuery;

    public void buildQuery(final XdStorageSqlSearchQuery query) throws XdStorageException {
        selectQuery = XdStorageSQLBuilderFactory.getInstance().getBuilder(XdStorageSQLBuilderName.SearchSelect)
                .build(clInfo, indexName, query, namingService, helper);
    }

    public List<IXdStorageSimpleWrapper> selectObjectReferences(final Connection connection, final XdStorageTransaction transaction)
            throws SQLException, XdStorageException {
        final PreparedStatement statement = connection.prepareStatement(selectQuery);

        final List<IXdStorageSimpleWrapper> result = new LinkedList<>();
        final ResultSet records = statement.executeQuery();
        while (records.next()) {
            result.add(getReference(helper.getObject(idFieldClass, records, 1), transaction));
        }
        return result;
    }

    private IXdStorageSimpleWrapper getReference(final Object id, final XdStorageTransaction transaction) throws XdStorageException {
        IXdStorageSimpleWrapper result = referencesProvider.getReference(cl, id, transaction);
        if (result == null) {
            result = referencesProvider.createAndRegisterReference(cl, idField, id, storage, transaction);
        }
        return result;
    }
}
