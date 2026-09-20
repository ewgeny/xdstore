package org.flib.xdstorage.sqlstorage.sql;

import org.flib.xdstorage.utils.XdStorageObjectField;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public interface IXdStorageSQLTypesHelper {

    String getPrimeryKeySQLType();

    String getSQLType(Class<?> cl);

    String getSQLType(XdStorageObjectField cl);

    String getStringSQLType(Class<?> cl, long length);

    boolean isSimpleType(XdStorageObjectField field);

    String simpleTypeValueToString(Object value);

    Object simpleTypeValueFromString(Class<?> cl, String value);

    String buildSqlCastFunction(String field, Class<?> cl);

    String buildForeignKeyConstraint(String fkFieldName, String referenceTable, String referenceTableFieldName);

    void setParameter(PreparedStatement statement, int index, Object value) throws SQLException;

    void setProperty(XdStorageObjectField field, Object object, ResultSet row) throws SQLException;

    void setProperty(XdStorageObjectField field, Object object, ResultSet row, int index) throws SQLException;

    void setProperty(XdStorageObjectField field, Class<?> cl, Object object, ResultSet row, int index) throws SQLException;

    Object getObject(Class<?> cl, ResultSet row, int index) throws SQLException;
}
