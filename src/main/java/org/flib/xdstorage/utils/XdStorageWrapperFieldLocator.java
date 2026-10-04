package org.flib.xdstorage.utils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class XdStorageWrapperFieldLocator {
    private static final Map<Class<?>, Field> simpleFields = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> unmodifiableFields = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> observableFields = new ConcurrentHashMap<>();

    private XdStorageWrapperFieldLocator() {}

    public static Field getSimpleWrapperObjectField(final Class<?> clWrapper) {
        return simpleFields.computeIfAbsent(clWrapper, cl -> {
            try { Field f = cl.getDeclaredField("object"); f.setAccessible(true); return f; } catch (Exception e) { return null; }
        });
    }

    public static Field getUnmodifiableWrapperObjectField(final Class<?> clWrapper) {
        return unmodifiableFields.computeIfAbsent(clWrapper, cl -> {
            try { Field f = cl.getDeclaredField("object"); f.setAccessible(true); return f; } catch (Exception e) { return null; }
        });
    }

    public static Field getObservableWrapperObjectField(final Class<?> clWrapper) {
        return observableFields.computeIfAbsent(clWrapper, cl -> {
            try { Field f = cl.getDeclaredField("object"); f.setAccessible(true); return f; } catch (Exception e) { return null; }
        });
    }
}
