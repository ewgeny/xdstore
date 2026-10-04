package org.flib.xdstorage.utils;

import java.util.IdentityHashMap;
import java.util.Map;

public final class XdStorageCloningContext {
    private static final ThreadLocal<Map<Object, Object>> cloningContext = ThreadLocal.withInitial(IdentityHashMap::new);

    private XdStorageCloningContext() {}

    public static Map<Object, Object> get() {
        return cloningContext.get();
    }

    public static boolean isRootCall() {
        return cloningContext.get().isEmpty();
    }

    public static void clear() {
        cloningContext.get().clear();
    }
}
