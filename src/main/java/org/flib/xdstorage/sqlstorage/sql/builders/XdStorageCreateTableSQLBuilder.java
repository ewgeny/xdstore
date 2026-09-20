package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLClassConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public class XdStorageCreateTableSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            final Object firstValue = values[0];
            final Class<?> cl;
            final XdStorageClassInfo clInfo;
            if (firstValue instanceof Class<?>) {
                cl = cast(firstValue);
                clInfo = null;
            } else {
                clInfo = cast(firstValue);
                cl = clInfo.getClazz();
            }
            sql = build(cl, clInfo, table, cast(values[2]), cast(values[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.CreateTable.name() + table;
    }

    private String build(final Class<?> cl, final XdStorageClassInfo clInfo, final String table, final IXdStorageSQLTypesHelper helper,
                         final XdStorageSQLConfiguration config) {
        final StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS ");

        sb.append(table).append(" (");
        if (cl == XdStorageTransaction.class) {
            sb.append("id ").append(helper.getPrimeryKeySQLType())
                    .append(" PRIMARY KEY, txname ").append(helper.getStringSQLType(String.class, 54))
                    .append(", txtimestamp ").append(helper.getSQLType(Long.class))
                    .append(", txclass ").append(helper.getSQLType(String.class))
                    .append(", txidxclass ").append(helper.getSQLType(String.class))
                    .append(", reference ").append(helper.getSQLType(Boolean.class))
                    .append(", txstate ").append(helper.getSQLType(String.class));
        } else {
            final XdStorageObjectIdField idField = clInfo.getIdField();
            final XdStorageSQLClassConfiguration classConfig = config.getClassConfig(cl);
            if (idField.getIdGeneretorType() != XdStorageIdGeneratorType.DATABASE_GENERATOR || classConfig.isMultiple() || classConfig.isParentDataSource()) {
                sb.append(idField.getName()).append(' ').append(getType(idField, helper)).append(" PRIMARY KEY").append(',');
            } else {
                sb.append(idField.getName()).append(' ').append(helper.getPrimeryKeySQLType()).append(" PRIMARY KEY").append(',');
            }

            // fields for cross datasource references
            sb.append("reference ").append(helper.getSQLType(Boolean.class)).append(',');
            sb.append("datasource ").append(helper.getStringSQLType(String.class, 255)).append(',');

            // fields of the object
            final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
            for (final XdStorageObjectField field : fields) {
                if (!field.isIdField() && helper.isSimpleType(field)) {
                    sb.append(field.getName()).append(' ').append(getType(field, helper)).append(',');
                }
            }
            sb.append("txname ").append(helper.getStringSQLType(String.class, 54));
        }
        sb.append(")");

        return sb.toString();
    }
}
