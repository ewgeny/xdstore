package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.idgeneration.XdStorageIdGeneratorType;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLClassConfiguration;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.XdStorageSQLBuilderName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.util.Collection;

public class XdStorageInsertSQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) {
        final String table = cast(values[1]);
        final String sqlId = buildSQLId(table);

        String sql = getSQL(sqlId);
        if (sql == null) {
            sql = build(cast(values[0]), cast(values[1]), cast(values[2]), cast(values[3]));
            putSQL(sqlId, sql);
        }
        return sql;
    }

    private String buildSQLId(final String table) {
        return XdStorageSQLBuilderName.Insert.name() + table;
    }

    private String build(final XdStorageClassInfo clInfo, final String table, final IXdStorageSQLTypesHelper helper,
                         final XdStorageSQLConfiguration config) {
        final StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO ").append(table).append('(');

        int count = 0;

        final XdStorageObjectIdField idField = clInfo.getIdField();
        final XdStorageSQLClassConfiguration classConfig = config.getClassConfig(clInfo.getClazz());
        if (idField.getIdGeneretorType() != XdStorageIdGeneratorType.DATABASE_GENERATOR || classConfig.isMultiple() || classConfig.isParentDataSource()) {
            sb.append(idField.getName()).append(',');
            ++count;
        }

        final Collection<XdStorageObjectField> fields = clInfo.getFields().values();
        for (final XdStorageObjectField field : fields) {
            if (!field.isIdField() && helper.isSimpleType(field)) {
                sb.append(field.getName()).append(',');
                ++count;
            }
        }
        sb.append("txname)");
        ++count;

        sb.append(" VALUES(");
        for (int i = 0; i < count; ++i) {
            sb.append('?');
            if (i < count - 1) {
                sb.append(",");
            }
        }
        sb.append(')');

        return sb.toString();
    }
}
