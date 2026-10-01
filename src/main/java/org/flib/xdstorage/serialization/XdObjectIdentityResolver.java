package org.flib.xdstorage.serialization;

import org.flib.xdstorage.transaction.IXdStorageTransaction;
import java.lang.reflect.Constructor;

public class XdObjectIdentityResolver {

    public Object resolveId(final String classObjectId, final String idValue) throws Exception {
        final Class<?> idClass = Class.forName(classObjectId);
        if (idClass == Class.class || org.flib.xdstorage.utils.XdStorageObjectUtils.isSimpleType(idClass, null)) {
            if (idClass == Class.class) {
                return Class.forName(idValue);
            } else {
                final Constructor<?> constructor = idClass.getConstructor(String.class);
                return constructor.newInstance(idValue);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public Object tryGetFromCache(final IXdStorageTransaction tx, final Class<?> targetClass, final Object objectId) throws Exception {
        if (tx != null && objectId != null) {
            // Прямой запрос к транзакционному кэшу сущностей ядра СУБД
            Object cachedObject = tx.getStorage().load((Class<Object>) targetClass, objectId);
            if (cachedObject != null) {
                return cachedObject;
            }
        }
        return null;
    }
}
