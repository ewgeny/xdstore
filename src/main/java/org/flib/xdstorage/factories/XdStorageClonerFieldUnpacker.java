package org.flib.xdstorage.factories;

import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

/**
 * Выделенный компонент декомпозиции СУБД (Поинт В).
 * Отвечает исключительно за рефлексивное восстановление POJO из прокси-оберток.
 */
public class XdStorageClonerFieldUnpacker {

    public static void unpackFields(final Object target, final Object src, final XdStorageClassInfo clInfo,
                                    final XdStorageSmartCloner cloner) throws Exception {

        final Map<String, XdStorageObjectField> fields = clInfo.getFields();
        for (final XdStorageObjectField field : fields.values()) {
            if (field.isIdField()) {
                continue;
            }
            final Object value = field.get(src);
            final Class<?> clValue = value != null ? XdStorageObjectUtils.getEntityClass(value.getClass()) : null;

            if (value == null || clValue.isPrimitive() || clValue.isEnum() || XdStorageObjectUtils.isSimpleType(clValue, value)) {
                field.set(target, value);
            } else if (clValue == Date.class) {
                field.set(target, new Date(((Date) value).getTime()));
            } else if (clValue.isArray()) {
                field.set(target, XdStorageClonerArrayProcessor.unwrapAndCloneArray(value, cloner));
            } else if (value instanceof Collection<?>) {
                field.set(target, XdStorageClonerCollectionProcessor.unwrapAndCloneCollection((Collection<?>) value, cloner));
            } else if (value instanceof Map<?, ?>) {
                field.set(target, XdStorageClonerCollectionProcessor.unwrapAndCloneMap((Map<?, ?>) value, cloner));
            } else {
                field.set(target, cloner.internalUnwrapAndCloneAsReference(value));
            }
        }
    }
}
