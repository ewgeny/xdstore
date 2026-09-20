package org.flib.xdstorage.sqlstorage.sql.builders.links;

import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.sqlstorage.sql.builders.XdStorageAbstractSQLBuilder;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class XdStorageCreateLinksTableSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), table, cast(values[2]), cast(values[3]), cast(values[4]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.CreateLinksTable.name() + table;
    }

    private String build(final XdStorageClassInfo clOwnerInfo, final String table, final String fieldName, final Class<?>[] classes,
                         final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");

        sb.append(table).append(" (");

        final XdStorageObjectIdField idFieldOwner = clOwnerInfo.getIdField();
        sb.append(idFieldOwner.getName()).append(' ').append(getType(idFieldOwner, helper)).append(',');

        int index = 0;
        if (classes.length > 1) {
            sb.append("key ").append(helper.getSQLType(classes[index++])).append(',');
        }

        final XdStorageClassInfo infoChild = XdStorageObjectUtils.getClassInfo(classes[index]);
        final XdStorageObjectIdField idFieldChild = infoChild.getIdField();
        if (idFieldChild.getIdGeneretorType() != XdStorageIdGeneratorType.DATABASE_GENERATOR) {
            sb.append(fieldName).append('_').append(idFieldChild.getName()).append(' ').append(getType(idFieldChild, helper)).append(',');
        } else {
            sb.append(fieldName).append('_').append(idFieldChild.getName()).append(' ').append(helper.getPrimeryKeySQLType()).append(',');
        }


        sb.append("txname ").append(helper.getStringSQLType(String.class, 54));
        sb.append(")");

        return sb.toString();
    }
}
