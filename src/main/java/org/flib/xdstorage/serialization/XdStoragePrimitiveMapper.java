package org.flib.xdstorage.serialization;

import java.util.HashMap;
import java.util.Map;

/**
 * Инкапсулирует логику сопоставления строковых названий
 * и встроенных классов примитивных типов Java.
 */
public final class XdStoragePrimitiveMapper {

    private static final Map<String, Class<?>> PRIMITIVE_TYPES = new HashMap<>();

    static {
        PRIMITIVE_TYPES.put(byte.class.getName(), byte.class);
        PRIMITIVE_TYPES.put(short.class.getName(), short.class);
        PRIMITIVE_TYPES.put(int.class.getName(), int.class);
        PRIMITIVE_TYPES.put(long.class.getName(), long.class);
        PRIMITIVE_TYPES.put(float.class.getName(), float.class);
        PRIMITIVE_TYPES.put(double.class.getName(), double.class);
        PRIMITIVE_TYPES.put(boolean.class.getName(), boolean.class);
        PRIMITIVE_TYPES.put(char.class.getName(), char.class);
    }

    private XdStoragePrimitiveMapper() {}

    public static Class<?> getPrimitiveType(final String className) {
        return PRIMITIVE_TYPES.get(className);
    }

    public static Object parsePrimitive(final String className, final String value) {
        switch (className) {
            case "byte":    return Byte.parseByte(value);
            case "short":   return Short.parseShort(value);
            case "int":     return Integer.parseInt(value);
            case "long":    return Long.parseLong(value);
            case "float":   return Float.parseFloat(value);
            case "double":  return Double.parseDouble(value);
            case "boolean": return Boolean.parseBoolean(value);
            case "char":    return value.isEmpty() ? '\0' : value.charAt(0);
            default:
                throw new IllegalArgumentException("Неподдерживаемый примитивный тип: " + className);
        }
    }
}
