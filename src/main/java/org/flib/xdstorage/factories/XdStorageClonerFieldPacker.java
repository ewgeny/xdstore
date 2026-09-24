package org.flib.xdstorage.factories;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Выделенный компонент декомпозиции СУБД (Поинт В).
 * Отвечает исключительно за рефлексивный перенос данных из POJO в прокси СУБД.
 */
public class XdStorageClonerFieldPacker {

    public static void packFields(final Object target, final Object src, final XdStorageClassInfo clInfo,
                                  final IXdStorage storage, final IXdStorageTransaction transaction,
                                  final XdStorageSmartCloner cloner) throws Exception {

        final Map<String, XdStorageObjectField> fields = clInfo.getFields();
        for (final XdStorageObjectField field : fields.values()) {
            if (field.isIdField()) {
                continue;
            }
            final Object value = field.get(src);
            final Class<?> clValue = value != null ? value.getClass() : null;

            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                field.set(target, value);
            } else if (clValue == Date.class) {
                field.set(target, new Date(((Date) value).getTime()));
            } else if (clValue.isArray()) {
                field.set(target, XdStorageClonerArrayProcessor.cloneAndWrapArray(value, storage, transaction, cloner));
            } else if (value instanceof Collection<?>) {
                field.set(target, XdStorageClonerCollectionProcessor.cloneAndWrapCollection((Collection<?>) value, storage, transaction, cloner));
            } else if (value instanceof Map<?, ?>) {
                field.set(target, XdStorageClonerCollectionProcessor.cloneAndWrapMap((Map<?, ?>) value, storage, transaction, cloner));
            } else {
                field.set(target, cloner.internalCloneAsReferenceAndWrapObject(value, storage, transaction));
            }
        }
    }
}
