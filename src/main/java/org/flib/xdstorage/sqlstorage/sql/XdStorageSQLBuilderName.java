package org.flib.xdstorage.sqlstorage.sql;

public enum XdStorageSQLBuilderName {

    SearchSelect(0),

    CreateTable(100),
    CreateInternalObjectsTable(100),
    CreateLinksTable(100),
    CreateCrossDatasourceFkTable(100),
    CreateSearchIndexTable(100),
    CreateIndexTable(100),

    AddForeignKey(150),


    RegisterResource(300),
    UpdateResourceByTransactionAndClass(500),
    UpdateResourceByTransactionAndClassAndIdxClass(500),


    InsertForUpdate(700),
    InsertForDelete(700),
    SelectInsertInternalObjectsByOwnerId(700),
    SelectInsertInternalObjectsByTransaction(700),
    InsertIndexForUpdate(1100),
    InsertIndexForDelete(1100),
    SelectInsertSearchIndexByObjectId(1100),
    SelectInsertSearchIndexByTransaction(1100),


    SelectInsertFromPrevStateByTransaction(1200),
    SelectInsertIndexFromPrevStateByTransaction(1200),
    SelectInsertCrossDatasourceFkFromPrevState(1700),
    SelectInsertCrossDatasourceReferenceFromPrevState(1700),


    SelectInsertCrossDatasourceReferenceForDelete(1800),
    SelectInsertCrossDatasourceFkForDelete(1800),
    SelectInsertLinksByOwnerId(1900),
    SelectInsertLinksByTransaction(1900),


    DeleteLinksByOwnerId(2100),
    DeleteIndexById(2100),
    DeleteSearchIndexByObjectId(2100),
    DeleteCrossDatasourceFk(2100),
    DeleteLinksByTransaction(2200),
    DeleteIndexByTransaction(2200),
    DeleteSearchIndexByTransaction(2200),

    DeleteCrossDatasourceFkByTransaction(2200),
    DeleteLinksByTransactionAndId(2200),
    DeleteByTransaction(2500),


    DeleteById(2700),
    DeleteInternalObjectsByTransaction(2700),
    DeleteInternalObjectsByOwnerId(2700),
    DeleteCrossDatasourceReferenceByTransaction(2700),
    DeleteCrossDatasourceReference(2700),


    UpdateById(3000),
    UpdateLinksByTransaction(3000),
    UpdateInternalObjectsByTransaction(3000),
    UpdateCrossDatasourceFkByTransaction(3000),
    UpdateCrossDatasourceReferenceByTransaction(3000),
    UpdateByTransaction(3000),
    UpdateIndexByTransaction(3000),
    UpdateIndexById(3000),
    UpdateSearchIndexByTransaction(3000),


    Insert(3500),
    InsertInternalObject(3500),
    InsertCrossDatasourceFk(3600),
    InsertCrossDatasourceReference(3600),
    InsertLink(3700),
    InsertIndex(3700),
    InsertSearchIndex(3700),


    SelectAll(10000),
    SelectById(10000),
    SelectLinks(10000),
    SelectInternalObjects(10000),
    SelectIndexById(10000),
    SelectIndex(10000),
    SelectSearchIndexByObjectId(10000)
    ;

    public final Integer priority;

    XdStorageSQLBuilderName(Integer priority) {
        this.priority = priority;
    }
}
