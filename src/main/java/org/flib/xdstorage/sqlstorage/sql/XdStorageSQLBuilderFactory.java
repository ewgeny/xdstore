package org.flib.xdstorage.sqlstorage.sql;

import org.flib.xdstorage.sqlstorage.sql.builders.*;
import org.flib.xdstorage.sqlstorage.sql.builders.fk.*;
import org.flib.xdstorage.sqlstorage.sql.builders.index.*;
import org.flib.xdstorage.sqlstorage.sql.builders.internal.*;
import org.flib.xdstorage.sqlstorage.sql.builders.links.*;
import org.flib.xdstorage.sqlstorage.sql.builders.references.*;
import org.flib.xdstorage.sqlstorage.sql.builders.resource.XdStorageRegisterResourceSQLBuilder;
import org.flib.xdstorage.sqlstorage.sql.builders.resource.XdStorageUpdateResourceByTransactionAndClassAndIdxClassSQLBuilder;
import org.flib.xdstorage.sqlstorage.sql.builders.resource.XdStorageUpdateResourceByTransactionAndClassSQLBuilder;
import org.flib.xdstorage.sqlstorage.sql.builders.search.*;

import java.util.HashMap;
import java.util.Map;

public class XdStorageSQLBuilderFactory {

    private static final XdStorageSQLBuilderFactory Instance = new XdStorageSQLBuilderFactory();

    public static XdStorageSQLBuilderFactory getInstance() {
        return Instance;
    }

    private final Map<XdStorageSQLBuilderName, IXdStorageSQLBuilder> builders;

    {
        builders = new HashMap<>();

        builders.put(XdStorageSQLBuilderName.SearchSelect, new XdStorageSearchSQLBuilder());

        builders.put(XdStorageSQLBuilderName.RegisterResource, new XdStorageRegisterResourceSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClass, new XdStorageUpdateResourceByTransactionAndClassSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateResourceByTransactionAndClassAndIdxClass, new XdStorageUpdateResourceByTransactionAndClassAndIdxClassSQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateTable, new XdStorageCreateTableSQLBuilder());
        builders.put(XdStorageSQLBuilderName.Insert, new XdStorageInsertSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertForUpdate, new XdStorageInsertForUpdateSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertForDelete, new XdStorageInsertForDeleteSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateById, new XdStorageUpdateByIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteById, new XdStorageDeleteByIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectAll, new XdStorageSelectAllSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectById, new XdStorageSelectByIdSQLBuilder());

        builders.put(XdStorageSQLBuilderName.AddForeignKey, new XdStorageAddForeignKeySQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateInternalObjectsTable, new XdStorageCreateInternalObjectsTableSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertInternalObjectsByOwnerId, new XdStorageSelectInsertInternalObjectsByOwnerIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertInternalObjectsByTransaction, new XdStorageSelectInsertInternalObjectsByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteInternalObjectsByOwnerId, new XdStorageDeleteInternalObjectsByOwnerIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertInternalObject, new XdStorageInsertInternalObjectSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateInternalObjectsByTransaction, new XdStorageUpdateInternalObjectsByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteInternalObjectsByTransaction, new XdStorageDeleteInternalObjectsByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInternalObjects, new XdStorageSelectInternalObjectsSQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateCrossDatasourceFkTable, new XdStorageCreateCrossDatasourceFkTableSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertCrossDatasourceFk, new XdStorageInsertCrossDatasourceFkSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteCrossDatasourceFk, new XdStorageDeleteCrossDatasourceFkSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteCrossDatasourceFkByTransaction, new XdStorageDeleteCrossDatasourceFkByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateCrossDatasourceFkByTransaction, new XdStorageUpdateCrossDatasourceFkByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkForDelete, new XdStorageSelectInsertCrossDatasourceFkForDeleteSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertCrossDatasourceFkFromPrevState, new XdStorageSelectInsertCrossDatasourceFkFromPrevStateSQLBuilder());

        builders.put(XdStorageSQLBuilderName.InsertCrossDatasourceReference, new XdStorageInsertCrossDatasourceReferenceSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteCrossDatasourceReference, new XdStorageDeleteCrossDatasourceReferenceSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceForDelete, new XdStorageSelectInsertCrossDatasourceReferenceForDeleteSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertCrossDatasourceReferenceFromPrevState, new XdStorageSelectInsertCrossDatasourceReferenceFromPrevStateSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateCrossDatasourceReferenceByTransaction, new XdStorageUpdateCrossDatasourceReferenceByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteCrossDatasourceReferenceByTransaction, new XdStorageDeleteCrossDatasourceReferenceByTransactionSQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateLinksTable, new XdStorageCreateLinksTableSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertLinksByOwnerId, new XdStorageSelectInsertLinksByOwnerIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertLinksByTransaction, new XdStorageSelectInsertLinksByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteLinksByOwnerId, new XdStorageDeleteLinksByOwnerIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertLink, new XdStorageInsertLinkSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateLinksByTransaction, new XdStorageUpdateLinksByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteLinksByTransaction, new XdStorageDeleteLinksByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteLinksByTransactionAndId, new XdStorageDeleteLinksByTransactionAndIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectLinks, new XdStorageSelectLinksSQLBuilder());

        builders.put(XdStorageSQLBuilderName.SelectInsertFromPrevStateByTransaction, new XdStorageSelectInsertFromPrevStateByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateByTransaction, new XdStorageUpdateByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteByTransaction, new XdStorageDeleteByTransactionSQLBuilder());

        builders.put(XdStorageSQLBuilderName.SelectInsertIndexFromPrevStateByTransaction, new XdStorageSelectInsertIndexFromPrevStateByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertIndex, new XdStorageInsertIndexSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateIndexById, new XdStorageUpdateIndexByIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteIndexById, new XdStorageDeleteIndexByIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertIndexForUpdate, new XdStorageInsertIndexForUpdateSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertIndexForDelete, new XdStorageInsertIndexForDeleteSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectIndexById, new XdStorageSelectIndexByIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectIndex, new XdStorageSelectIndexSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateIndexByTransaction, new XdStorageUpdateIndexByTransactionSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteIndexByTransaction, new XdStorageDeleteIndexByTransactionSQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateIndexTable, new XdStorageCreateIndexTableSQLBuilder());

        builders.put(XdStorageSQLBuilderName.CreateSearchIndexTable, new XdStorageCreateSearchIndexTablesSQLBuilder());
        builders.put(XdStorageSQLBuilderName.InsertSearchIndex, new XdStorageInsertSearchIndexSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteSearchIndexByObjectId, new XdStorageDeleteSearchIndexByObjectIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertSearchIndexByObjectId, new XdStorageSelectInsertSearchIndexByObjectIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.DeleteSearchIndexByTransaction, new XdStorageDeleteSearchIndexByTransactionIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectInsertSearchIndexByTransaction, new XdStorageSelectInsertSearchIndexByTransactionIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.UpdateSearchIndexByTransaction, new XdStorageUpdateSearchIndexByTransactionIdSQLBuilder());
        builders.put(XdStorageSQLBuilderName.SelectSearchIndexByObjectId, new XdStorageSelectSearchIndexByObjectIdSQLBuilder());
    }

    public IXdStorageSQLBuilder getBuilder(final XdStorageSQLBuilderName name) {
        return builders.get(name);
    }
}
