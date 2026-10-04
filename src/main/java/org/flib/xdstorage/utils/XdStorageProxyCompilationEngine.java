package org.flib.xdstorage.utils;

import org.flib.xdstorage.code.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class XdStorageProxyCompilationEngine {
    private static final Map<Class<?>, Class<?>> classesUnmodifiableWrappers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> classesSimpleWrappers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Class<?>> classesObservableWrappers = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> checkingWrappableMethod = new ConcurrentHashMap<>();

    private static final ReentrantLock COMPILATION_LOCK = new ReentrantLock();
    private static final ThreadLocal<Set<Class<?>>> currentThreadCompilationStack = ThreadLocal.withInitial(HashSet::new);

    private XdStorageProxyCompilationEngine() {}

    public static Class<?> getClassSimpleWrapper(final Class<?> cl) throws IOException {
        Class<?> existing = classesSimpleWrappers.get(cl);
        if (existing != null) return existing;

        if (currentThreadCompilationStack.get().contains(cl)) return XdStorageDummySimpleWrapper.class;

        COMPILATION_LOCK.lock();
        try {
            existing = classesSimpleWrappers.get(cl);
            if (existing != null) return existing;

            currentThreadCompilationStack.get().add(cl);
            try {
                Map<Class<?>, Class<?>> gen = XdStorageClassGenerator.generateSimpleWrapper(cl);
                if (gen.isEmpty()) {
                    classesSimpleWrappers.put(cl, XdStorageDummySimpleWrapper.class);
                } else {
                    for (Map.Entry<Class<?>, Class<?>> e : gen.entrySet()) {
                        if (e.getValue() != null) classesSimpleWrappers.put(e.getKey(), e.getValue());
                    }
                }
            } finally { currentThreadCompilationStack.get().remove(cl); }
            return classesSimpleWrappers.get(cl);
        } catch (IOException e) { classesSimpleWrappers.remove(cl); throw e; } finally { COMPILATION_LOCK.unlock(); }
    }

    public static Class<?> getClassUnmodifiableWrapper(final Class<?> cl) throws IOException {
        Class<?> existing = classesUnmodifiableWrappers.get(cl);
        if (existing != null) return existing;

        if (currentThreadCompilationStack.get().contains(cl)) return XdStorageDummyUnmodifiableWrapper.class;

        COMPILATION_LOCK.lock();
        try {
            existing = classesUnmodifiableWrappers.get(cl);
            if (existing != null) return existing;

            currentThreadCompilationStack.get().add(cl);
            try {
                Map<Class<?>, Class<?>> gen = XdStorageClassGenerator.generateUnmodifiableWrapper(cl);
                if (gen.isEmpty()) {
                    classesUnmodifiableWrappers.put(cl, XdStorageDummyUnmodifiableWrapper.class);
                } else {
                    for (Map.Entry<Class<?>, Class<?>> e : gen.entrySet()) {
                        if (e.getValue() != null) classesUnmodifiableWrappers.put(e.getKey(), e.getValue());
                    }
                }
            } finally { currentThreadCompilationStack.get().remove(cl); }
            return classesUnmodifiableWrappers.get(cl);
        } catch (IOException e) { classesUnmodifiableWrappers.remove(cl); throw e; } finally { COMPILATION_LOCK.unlock(); }
    }

    public static Class<?> getClassObservableWrapper(final Class<?> cl) throws IOException {
        Class<?> existing = classesObservableWrappers.get(cl);
        if (existing != null) return existing;

        COMPILATION_LOCK.lock();
        try {
            existing = classesObservableWrappers.get(cl);
            if (existing != null) return existing;

            Map<Class<?>, Class<?>> gen = XdStorageClassGenerator.generateObservableWrapper(cl);
            for (Map.Entry<Class<?>, Class<?>> e : gen.entrySet()) {
                if (e.getValue() != null) classesObservableWrappers.put(e.getKey(), e.getValue());
            }
            return classesObservableWrappers.get(cl);
        } finally { COMPILATION_LOCK.unlock(); }
    }

    public static Method getCheckingWrappableMethod(final Class<?> wrapperClass) {
        return checkingWrappableMethod.computeIfAbsent(wrapperClass, cl -> {
            try { return cl.getDeclaredMethod("isDummyUnmodifiableWrapper"); } catch (Exception e) { return null; }
        });
    }

    public static Method getCheckingSimpleWrappableMethod(final Class<?> wrapperClass) {
        return checkingWrappableMethod.computeIfAbsent(wrapperClass, cl -> {
            try { return cl.getDeclaredMethod("isDummySimpleWrapper"); } catch (Exception e) { return null; }
        });
    }

    public static Class<?> getWrappedClass(final Class<?> clWrapper) {
        for (final Map.Entry<Class<?>, Class<?>> entry : classesUnmodifiableWrappers.entrySet()) {
            if (entry.getValue() == clWrapper) return entry.getKey();
        }
        return null;
    }
}
