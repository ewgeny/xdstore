package org.flib.xdstorage.sqlstorage.sql.builders;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;

public class XdStorageAddForeignKeySQLBuilder extends XdStorageAbstractSQLBuilder {

    @Override
    public String build(final Object... values) throws XdStorageException {
        return build(cast(values[0]), cast(values[1]), cast(values[2]), cast(values[3]), cast(values[4]));
    }

    public String build(final String fkTable, final String fkField, final String referenceTable, final String referenceField,
                        final IXdStorageSQLTypesHelper helper) {
        final StringBuilder sb = new StringBuilder();

        final String conname = fkTable.toLowerCase() + "_" + referenceTable.toLowerCase() + "_" + referenceField.toLowerCase();

        sb.append("DO $$\n\rBEGIN\n\r");


        sb.append("IF NOT EXISTS ( SELECT 1 FROM pg_constraint WHERE conname = '").append(conname).append("') THEN\n\r");

        sb.append("IF EXISTS ( SELECT 1 FROM pg_tables t1 INNER JOIN pg_tables t2 ON 1 = 1 " +
                "WHERE t1.tablename = '").append(fkTable.toLowerCase()).append("' AND t2.tablename = '").append(referenceTable.toLowerCase()).append("') THEN\n\r");
        sb.append("ALTER TABLE ").append(fkTable).append(" ADD CONSTRAINT ").append(conname).append(' ')
                .append(helper.buildForeignKeyConstraint(fkField, referenceTable, referenceField))
                .append(";\n\r");
        sb.append("END IF;\n\r");

        sb.append("END IF;\n\r");


        sb.append("END;\n\r$$;\n\r");

        return sb.toString();
    }
}
