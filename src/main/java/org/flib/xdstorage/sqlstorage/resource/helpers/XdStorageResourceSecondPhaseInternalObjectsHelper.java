package org.flib.xdstorage.sqlstorage.resource.helpers;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.XdStorageTransactionResourceChanges;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

public class XdStorageResourceSecondPhaseInternalObjectsHelper {

    public static void collectLinksForSecondPhaseCommit(final XdStorageTransactionResourceChanges record,
                                                        final Map<XdStorageObjectField, Collection<Object>> links,
                                                        final IXdStorageSQLTypesHelper helper) {
        for (final XdStorageObjectChange change : record.getChangesObjects()) {
            if (change.type == XdStorageObjectOperationType.Insert) {
                collectLinksForSecondPhaseCommit(change.newObject, links, helper);
            } else if (change.type == XdStorageObjectOperationType.Update) {
                collectLinksForSecondPhaseCommit(change.newObject, links, helper);
            } else {
                collectLinksForSecondPhaseCommit(change.oldObject, links, helper);
            }
        }
    }

    private static void collectLinksForSecondPhaseCommit(final Object object, final Map<XdStorageObjectField, Collection<Object>> links,
                                                         final IXdStorageSQLTypesHelper helper) {
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(object.getClass());
        final XdStorageObjectIdField idField = clInfo.getIdField();

        final Object id = idField.get(object);
        for (final XdStorageObjectField field : clInfo.getFields().values()) {
            if (helper.isSimpleType(field)) {
                continue;
            }

            Collection<Object> objectIds = links.get(field);
            if (objectIds == null) {
                links.put(field, objectIds = new ArrayList<>());
            }
            objectIds.add(id);
        }
    }
}
