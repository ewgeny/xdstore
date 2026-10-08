package org.flib.xdstorage.helpers;

import org.apache.commons.lang3.ClassUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Date;

/**
 * Стандартный системный хелпер конвертации простых типов СУБД.
 * Полностью поддерживает динамическую материализацию примитивов и перечислений (Enum).
 */
public class XdStorageDefaultSimpleTypeHelper implements IXdStorageSimpleTypeHelper {

    private static final Logger log = LogManager.getLogger(XdStorageDefaultSimpleTypeHelper.class);

    @Override
    public boolean isSimpleType(final Class<?> cl, final Object object) {
        return cl == Class.class || cl == Date.class || cl.isEnum() || XdStorageObjectUtils.isSimpleType(cl, object);
    }

    @Override
    public String simpleTypeToString(final Object object) {
        if (object instanceof Class<?>) {
            return ((Class<?>) object).getName();
        }
        return object.getClass() == Date.class ? String.valueOf(((Date) object).getTime()) : object.toString();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object simpleTypeFromString(final Class<?> cl, final String value) {
        if (cl == Class.class) {
            try {
                return ClassUtils.getClass(value);
            } catch (ClassNotFoundException e) {
                log.error("class not found", e);
                return null;
            }
        }
        if (cl == Boolean.class || cl == boolean.class)
            return Boolean.valueOf(value);
        if (cl == Character.class || cl == char.class)
            return Character.valueOf(value.charAt(0));
        if (cl == String.class)
            return value;
        if (cl == Date.class)
            return new Date(Long.valueOf(value));
        if (cl.isEnum()) {
            try {
                final Method m = cl.getMethod("valueOf", String.class);
                return m.invoke(cl, value);
            } catch (Exception e) {
                log.error("Failed to restore enum constant for class: " + cl.getName(), e);
                return null;
            }
        }

        try {
            final Constructor<?> c = cl.getConstructor(String.class);
            return c.newInstance(value);
        } catch (Exception e) {
            log.error("simple type from string error", e);
        }
        return null;
    }
}
