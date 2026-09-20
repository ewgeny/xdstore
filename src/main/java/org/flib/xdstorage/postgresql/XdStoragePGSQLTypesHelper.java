package org.flib.xdstorage.postgresql;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageStringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class XdStoragePGSQLTypesHelper implements IXdStorageSQLTypesHelper {

    private final Map<Class<?>, Helper> mappingClassToSQLType = new HashMap<>();

    {
        mappingClassToSQLType.put(String.class, new StringHelper());
        mappingClassToSQLType.put(BigDecimal.class, new BigDecimalHelper());
        mappingClassToSQLType.put(Double.class, new DoubleHelper());
        mappingClassToSQLType.put(Float.class, new FloatHelper());
        mappingClassToSQLType.put(Long.class, new LongHelper());
        mappingClassToSQLType.put(Integer.class, new IntegerHelper());
        mappingClassToSQLType.put(Short.class, new ShortHelper());
        mappingClassToSQLType.put(Boolean.class, new BooleanHelper());
        mappingClassToSQLType.put(Date.class, new DateHelper());
        mappingClassToSQLType.put(char[].class, new CharArrayHelper());
        mappingClassToSQLType.put(char.class, new CharHelper());
        mappingClassToSQLType.put(byte[].class, new ByteArrayHelper());
        mappingClassToSQLType.put(byte.class, new ByteHelper());
        mappingClassToSQLType.put(double.class, new DoubleHelper());
        mappingClassToSQLType.put(float.class, new FloatHelper());
        mappingClassToSQLType.put(long.class, new LongHelper());
        mappingClassToSQLType.put(int.class, new IntegerHelper());
        mappingClassToSQLType.put(short.class, new ShortHelper());
        mappingClassToSQLType.put(boolean.class, new BooleanHelper());
        mappingClassToSQLType.put(Class.class, new ClassHelper());
    }

    private final Map<Class<?>, Helper> mappingEnumClassToSQLType = new ConcurrentHashMap<>();

    private final ObjectHelper defaultHelper = new ObjectHelper();

    @Override
    public String getPrimeryKeySQLType() {
        return "SERIAL";
    }

    @Override
    public String getSQLType(final Class<?> cl) {
        final Helper type;
        if (cl.isEnum()) {
            type = getEnumClassHelper(cl);
        } else {
            type = mappingClassToSQLType.get(cl);
        }
        if (cl == String.class || cl == char[].class) {
            return type.getTypeName() + "(255)";
        }
        return type.getTypeName();
    }

    @Override
    public String getSQLType(final XdStorageObjectField field) {
        final Class<?> cl = field.getFieldInfo().getValueClass();
        final Helper type;
        if (cl.isEnum()) {
            type = getEnumClassHelper(cl);
        } else {
            type = mappingClassToSQLType.get(cl);
        }
        if (field.isStringField()) {
            return type.getTypeName() + "(" + field.getStringSize() + ")";
        }
        return type.getTypeName();
    }

    @Override
    public String getStringSQLType(final Class<?> cl, final long length) {
        final Helper type;
        if (cl.isEnum()) {
            type = getEnumClassHelper(cl);
        } else {
            type = mappingClassToSQLType.get(cl);
        }
        if (cl == String.class || cl == char[].class) {
            return type.getTypeName() + "(" + length + ")";
        }
        return type.getTypeName();
    }

    @Override
    public boolean isSimpleType(final XdStorageObjectField field) {
        final Class<?> cl = field.getFieldInfo().getValueClass();
        return cl.isEnum() || mappingClassToSQLType.containsKey(cl);
    }

    @Override
    public String simpleTypeValueToString(final Object value) {
        final Class<?> cl = value.getClass();
        final Helper type;
        if (cl.isEnum()) {
            type = getEnumClassHelper(cl);
        } else {
            type = mappingClassToSQLType.get(cl);
        }
        return type.toString(value);
    }

    @Override
    public Object simpleTypeValueFromString(final Class<?> cl, final String value) {
        final Helper type;
        if (cl.isEnum()) {
            type = getEnumClassHelper(cl);
        } else {
            type = mappingClassToSQLType.get(cl);
        }
        return type.fromString(value);
    }

    @Override
    public String buildSqlCastFunction(final String field, final Class<?> cl) {
        final String castFunction;
        if (cl.isEnum()) {
            castFunction = "CAST(" + field + " AS VARCHAR)";
        } else {
            castFunction = new StringBuilder("CAST(").append(field).append(" AS ").append(mappingClassToSQLType.get(cl)).append(')').toString();
        }
        return castFunction;
    }

    @Override
    public String buildForeignKeyConstraint(final String fkFieldName, final String referenceTable, final String referenceTableFieldName) {
        return "FOREIGN KEY (" + fkFieldName + ") REFERENCES " + referenceTable + " (" + referenceTableFieldName + ")";
    }

    @Override
    public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
        final Helper helper;
        if (value != null) {
            final Class<?> cl = value.getClass();
            if (cl.isEnum()) {
                helper = getEnumClassHelper(cl);
            } else {
                helper = mappingClassToSQLType.get(cl);
            }
        } else {
            helper = defaultHelper;
        }
        helper.setParameter(statement, index, value);
    }

    @Override
    public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
        final Class<?> cl = field.getFieldInfo().getValueClass();
        final Helper helper;
        if (cl.isEnum()) {
            helper = getEnumClassHelper(cl);
        } else {
            helper = mappingClassToSQLType.get(cl);
        }
        helper.setProperty(field, object, row);
    }

    @Override
    public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
        final Class<?> cl = field.getFieldInfo().getValueClass();
        final Helper helper;
        if (cl.isEnum()) {
            helper = getEnumClassHelper(cl);
        } else {
            helper = mappingClassToSQLType.get(cl);
        }
        helper.setProperty(field, object, row, index);
    }

    @Override
    public void setProperty(final XdStorageObjectField field, final Class<?> cl, final Object object, final ResultSet row, final int index) throws SQLException {
        final Helper helper;
        if (cl.isEnum()) {
            helper = getEnumClassHelper(cl);
        } else {
            helper = mappingClassToSQLType.get(cl);
        }
        helper.setProperty(field, object, row, index);
    }

    @Override
    public Object getObject(final Class<?> cl, final ResultSet row, final int index) throws SQLException {
        final Helper helper;
        if (cl.isEnum()) {
            helper = getEnumClassHelper(cl);
        } else {
            helper = mappingClassToSQLType.get(cl);
        }
        return helper.getObject(row, index);
    }

    private Helper getEnumClassHelper(final Class<?> cl) {
        Helper helper = mappingEnumClassToSQLType.get(cl);
        if (helper == null) {
            mappingEnumClassToSQLType.putIfAbsent(cl, new EnumHelper(cl));
            helper = mappingEnumClassToSQLType.get(cl);
        }
        return helper;
    }

    private interface Helper {

        String getTypeName();

        void setParameter(PreparedStatement statement, int index, Object value) throws SQLException;

        void setProperty(XdStorageObjectField field, Object object, ResultSet row) throws SQLException;

        void setProperty(XdStorageObjectField field, Object object, ResultSet row, int index) throws SQLException;

        Object getObject(ResultSet row, int index) throws SQLException;

        String toString(Object value);

        Object fromString(String value);
    }

    private class EnumHelper implements Helper {

        private final Class<?> enumClass;

        private final Method name;

        private final Method valueOf;

        public EnumHelper(final Class<?> enumClass) {
            this.enumClass = enumClass;

            try {
                name = Enum.class.getDeclaredMethod("name");
            } catch (final NoSuchMethodException e) {
                throw new XdStorageRuntimeException(e);
            }

            try {
                valueOf = enumClass.getDeclaredMethod("valueOf", String.class);
            } catch (final NoSuchMethodException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public String getTypeName() {
            return "VARCHAR(50)";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            try {
                statement.setString(index, (String) name.invoke(value));
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public void setProperty(XdStorageObjectField field, Object object, ResultSet row) throws SQLException {
            try {
                final String fieldName = field.getName();
                if (row.getObject(fieldName) != null) {
                    field.set(object, valueOf.invoke(row.getString(fieldName)));
                }
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            try {
                if (row.getObject(index) != null) {
                    field.set(object, valueOf.invoke(row.getString(index)));
                }
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            try {
                return row.getObject(index) != null ? valueOf.invoke(row.getString(index)) : null;
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        public String toString(final Object value) {
            return ((Enum)enumClass.cast(value)).name();
        }

        public Object fromString(final String value) {
            try {
                return valueOf.invoke(value);
            } catch (final IllegalAccessException | InvocationTargetException e) {
                throw new XdStorageRuntimeException(e);
            }
        }
    }

    private static class ObjectHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setObject(index, value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            field.set(object, row.getObject(field.getName()));
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            field.set(object, row.getObject(index));
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index);
        }

        public String toString(final Object value) {
            throw new XdStorageRuntimeException("object of " + value.getClass() + " cannot be serialized to string");
        }

        public Object fromString(final String value) {
            throw new XdStorageRuntimeException("object of class java.lang.Object cannot be serialized from string");
        }
    }

    private static class StringHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setString(index, (String) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getString(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getString(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getString(index) : null;
        }

        public String toString(final Object value) {
            return (String) value;
        }

        public Object fromString(final String value) {
            return value;
        }
    }

    private static class BigDecimalHelper implements Helper {

        @Override
        public String getTypeName() {
            return "DECIMAL";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setBigDecimal(index, (BigDecimal) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getBigDecimal(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getBigDecimal(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getBigDecimal(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return new BigDecimal(value);
        }
    }

    private static class LongHelper implements Helper {

        @Override
        public String getTypeName() {
            return "DECIMAL";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setLong(index, (Long) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getLong(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getLong(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getLong(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Long.parseLong(value);
        }
    }

    private static class IntegerHelper implements Helper {

        @Override
        public String getTypeName() {
            return "INTEGER";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setInt(index, (Integer) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getInt(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getInt(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getInt(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Integer.parseInt(value);
        }
    }

    private static class ShortHelper implements Helper {

        @Override
        public String getTypeName() {
            return "SMALLINT";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setShort(index, (Short) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getShort(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getShort(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getShort(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Short.parseShort(value);
        }
    }

    private static class BooleanHelper implements Helper {

        @Override
        public String getTypeName() {
            return "BOOLEAN";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setBoolean(index, (Boolean) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getBoolean(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getBoolean(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getBoolean(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Boolean.parseBoolean(value);
        }
    }

    private static class DateHelper implements Helper {

        @Override
        public String getTypeName() {
            return "TIMESTAMP";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()));
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, new java.util.Date(row.getTimestamp(fieldName).getTime()));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, new java.util.Date(row.getTimestamp(index).getTime()));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? new java.util.Date(row.getTimestamp(index).getTime()) : null;
        }

        public String toString(final Object value) {
            return String.valueOf(((Date)value).getTime());
        }

        public Object fromString(final String value) {
            return new Date(Long.parseLong(value));
        }
    }

    private static class CharArrayHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR(4000)";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setString(index, String.valueOf((char[]) value));
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                final String str = row.getString(fieldName);
                char[] dst = new char[str.length()];
                str.getChars(0, dst.length, dst, 0);
                field.set(object, dst);
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                final String str = row.getString(index);
                char[] dst = new char[str.length()];
                str.getChars(0, dst.length, dst, 0);
                field.set(object, dst);
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                final String str = row.getString(index);
                char[] dst = new char[str.length()];
                str.getChars(0, dst.length, dst, 0);
                return dst;
            }
            return null;
        }

        public String toString(final Object value) {
            return new String((char[])value);
        }

        public Object fromString(final String value) {
            final char[] res = new char[value.length()];
            value.getChars(0, value.length(), res, 0);
            return res;
        }
    }

    private static class CharHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR(1)";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setString(index, String.valueOf((char) value));
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                final String str = row.getString(fieldName);
                char[] dst = new char[str.length()];
                str.getChars(0, dst.length, dst, 0);
                field.set(object, dst);
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                final String str = row.getString(index);
                char[] dst = new char[str.length()];
                str.getChars(0, dst.length, dst, 0);
                field.set(object, dst);
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                final String str = row.getString(index);
                return str.charAt(0);
            }
            return null;
        }

        public String toString(final Object value) {
            return String.valueOf((char) value);
        }

        public Object fromString(final String value) {
            return value.charAt(0);
        }
    }

    private static class ByteArrayHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR(4000)";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setBytes(index, (byte[]) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getBytes(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getBytes(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getBytes(index) : null;
        }

        public String toString(final Object value) {
            return new String((byte[])value, Charset.forName("utf-8"));
        }

        public Object fromString(final String value) {
            return value.getBytes(Charset.forName("utf-8"));
        }
    }

    private static class ByteHelper implements Helper {

        @Override
        public String getTypeName() {
            return "BYTEA";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setByte(index, (byte) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getByte(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getByte(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getByte(index) : null;
        }

        public String toString(final Object value) {
            final byte[] arr = new byte[]{ (byte)value };
            return new String((byte[])arr, Charset.forName("utf-8"));
        }

        public Object fromString(final String value) {
            return value.getBytes(Charset.forName("utf-8"))[0];
        }
    }

    private static class DoubleHelper implements Helper {

        @Override
        public String getTypeName() {
            return "DECIMAL";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setDouble(index, (Double) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getDouble(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getDouble(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getDouble(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Double.valueOf(value);
        }
    }

    private static class FloatHelper implements Helper {

        @Override
        public String getTypeName() {
            return "DECIMAL";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setFloat(index, (Float) value);
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            final String fieldName = field.getName();
            if (row.getObject(fieldName) != null) {
                field.set(object, row.getFloat(fieldName));
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            if (row.getObject(index) != null) {
                field.set(object, row.getFloat(index));
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            return row.getObject(index) != null ? row.getFloat(index) : null;
        }

        public String toString(final Object value) {
            return value.toString();
        }

        public Object fromString(final String value) {
            return Float.valueOf(value);
        }
    }

    private static class ClassHelper implements Helper {

        @Override
        public String getTypeName() {
            return "VARCHAR(255)";
        }

        @Override
        public void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
            statement.setString(index, ((Class) value).getName());
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row) throws SQLException {
            try {
                final String fieldName = field.getName();
                if (!XdStorageStringUtils.isBlank(row.getString(fieldName))) {
                    field.set(object, Class.forName(row.getString(fieldName)));
                }
            } catch (final ClassNotFoundException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public void setProperty(final XdStorageObjectField field, final Object object, final ResultSet row, final int index) throws SQLException {
            try {
                if (!XdStorageStringUtils.isBlank(row.getString(index))) {
                    field.set(object, Class.forName(row.getString(index)));
                }
            } catch (final ClassNotFoundException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        @Override
        public Object getObject(final ResultSet row, final int index) throws SQLException {
            try {
                if (!XdStorageStringUtils.isBlank(row.getString(index))) {
                    return Class.forName(row.getString(index));
                }
                return null;
            } catch (final ClassNotFoundException e) {
                throw new XdStorageRuntimeException(e);
            }
        }

        private final Map<Class<?>, Class<?>> mappingPrimitiveToObjective = new HashMap<>();

        {
            mappingPrimitiveToObjective.put(char[].class, String.class);
            mappingPrimitiveToObjective.put(char.class, String.class);
            mappingPrimitiveToObjective.put(byte[].class, String.class);
            mappingPrimitiveToObjective.put(byte.class, String.class);
            mappingPrimitiveToObjective.put(double.class, Double.class);
            mappingPrimitiveToObjective.put(float.class, Float.class);
            mappingPrimitiveToObjective.put(long.class, Long.class);
            mappingPrimitiveToObjective.put(int.class, Integer.class);
            mappingPrimitiveToObjective.put(short.class, Short.class);
            mappingPrimitiveToObjective.put(boolean.class, Boolean.class);
        }

        public String toString(final Object value) {
            final Class<?> cl = ((Class<?>) value);
            return cl.isPrimitive() ? mappingPrimitiveToObjective.get(cl).getName() : cl.getName();
        }

        public Object fromString(final String value) {
            try {
                return Class.forName(value);
            } catch (final ClassNotFoundException e) {
                throw new XdStorageRuntimeException(e);
            }
        }
    }
}
