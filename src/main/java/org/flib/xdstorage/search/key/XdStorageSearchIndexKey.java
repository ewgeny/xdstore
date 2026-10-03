package org.flib.xdstorage.search.key;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;

@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreWithParentObject)
public class XdStorageSearchIndexKey implements Comparable<XdStorageSearchIndexKey> {

    private Object value;

    private Object objectId;

    private XdStorageSearchIndexOperationType operation;

    public XdStorageSearchIndexKey() {

    }

    public XdStorageSearchIndexKey(final Object value, final Object objectId) {
        this(value, objectId, null);
    }

    public XdStorageSearchIndexKey(final Object value, final Object objectId, final XdStorageSearchIndexOperationType operation) {
        this.value = value;
        this.objectId = objectId;
        this.operation = operation;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    public Object getObjectId() {
        return objectId;
    }

    public void setObjectId(final Object objectId) {
        this.objectId = objectId;
    }

    public void setOperation(final XdStorageSearchIndexOperationType operation) {
        this.operation = operation;
    }

    @Override
    public int compareTo(final XdStorageSearchIndexKey o) {
        // === АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД (Корень всех сбоев маршрутизации Б+ Дерева): ===
        // Гарантируем абсолютную симметричность контракта compareTo для всех типов операций (Insert/Search/Update)!
        // Сначала сравниваем ключи по первичному значению индексируемого поля 'value'.
        int result = this.value == null ? (o.value == null ? 0 : -1) : (o.value == null ? 1 : ((Comparable) this.value).compareTo(o.value));

        // Если первичные значения равны, и у ОБЕИХ сравниваемых записей присутствуют идентификаторы объектов objectId,
        // мы ОБЯЗАНЫ выполнить строгое уточняющее сравнение по objectId (ID планеты), вне зависимости от флагов операций!
        // Прежняя логика игнорировала objectId в режиме Search, что ломало топологию B+ Дерева и вызывало крах readByReference!
        if (result == 0 && this.objectId != null && o.objectId != null) {
            result = ((Comparable) this.objectId).compareTo(o.objectId);
        }

        return result;
    }


    @Override
    public String toString() {
        return objectId.toString();
    }
}
