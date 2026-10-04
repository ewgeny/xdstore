package org.flib.xdstorage.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class XdStorageReflectionFieldScanner {
    private XdStorageReflectionFieldScanner() {}

    public static Map<String, XdStorageObjectField> scanFields(final Class<?> cl) {
        final Map<String, XdStorageObjectField> properties = new HashMap<>();
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            for (final Method getter : clazz.getDeclaredMethods()) {
                final String name = getter.getName();
                if (getter.getParameterTypes().length == 0 && (name.startsWith("get") || name.startsWith("is"))) {
                    final Class<?> tmp = getter.getReturnType();
                    if (tmp == void.class || tmp == Void.class) continue;
                    try {
                        final String fieldName;
                        Method setter;
                        if (name.startsWith("get")) {
                            setter = clazz.getMethod("set" + name.substring(3), tmp);
                            fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
                        } else {
                            setter = clazz.getMethod("set" + name.substring(2), tmp);
                            fieldName = name.substring(2, 3).toLowerCase() + name.substring(3);
                        }

                        Field field = scanFieldInHierarchy(clazz, fieldName);
                        if (field != null) {
                            properties.put(fieldName, new XdStorageObjectField(field, setter, getter, XdStorageClassMetadataRegistry.buildFieldInfo(field, getter)));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return properties;
    }

    private static Field scanFieldInHierarchy(Class<?> clazz, String fieldName) {
        for (Class<?> sc = clazz; !searchClassEqualsObject(sc); sc = sc.getSuperclass()) {
            try {
                Field f = sc.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static boolean searchClassEqualsObject(Class<?> sc) {
        return sc == null || sc.equals(Object.class);
    }
}
