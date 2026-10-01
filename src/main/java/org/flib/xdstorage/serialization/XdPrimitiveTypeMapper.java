package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import java.lang.reflect.Method;

public class XdPrimitiveTypeMapper {
    private final IXdStorageSimpleTypeHelper simpleHelper;

    public XdPrimitiveTypeMapper(final IXdStorageSimpleTypeHelper simpleHelper) {
        this.simpleHelper = simpleHelper;
    }

    public Object toSimple(final Class<?> cl, final String val) {
        return simpleHelper.simpleTypeFromString(cl, val);
    }

    public Object toPrimitive(final String clName, final String val) {
        if (clName.equals("byte")) return Byte.parseByte(val);
        if (clName.equals("short")) return Short.parseShort(val);
        if (clName.equals("int")) return Integer.parseInt(val);
        if (clName.equals("long")) return Long.parseLong(val);
        if (clName.equals("float")) return Float.parseFloat(val);
        if (clName.equals("double")) return Double.parseDouble(val);
        if (clName.equals("boolean")) return Boolean.parseBoolean(val);
        if (clName.equals("char")) return val.charAt(0);
        return null;
    }

    public Class<?> getPrimitiveType(final String className) {
        if (className.equals(byte.class.getName())) return byte.class;
        if (className.equals(short.class.getName())) return short.class;
        if (className.equals(int.class.getName())) return int.class;
        if (className.equals(long.class.getName())) return long.class;
        if (className.equals(float.class.getName())) return float.class;
        if (className.equals(double.class.getName())) return double.class;
        if (className.equals(boolean.class.getName())) return boolean.class;
        if (className.equals(char.class.getName())) return char.class;
        return null;
    }

    public Object toEnum(final String clName, final String val) throws Exception {
        final Class<?> cl = Class.forName(clName);
        final Method m = cl.getMethod("valueOf", String.class);
        return m.invoke(cl, val);
    }
}
