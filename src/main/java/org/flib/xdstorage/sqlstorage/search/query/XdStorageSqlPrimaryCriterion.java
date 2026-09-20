package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;

import java.util.List;

public class XdStorageSqlPrimaryCriterion implements IXdStorageSqlPrimaryCriterion {

    private final XdStorageSqlCriterionOperator operator;

    private final Object[] values;

    public XdStorageSqlPrimaryCriterion(final XdStorageSqlCriterionOperator operator, final Object... values) {
        this.operator = operator;
        this.values = values;
    }

    public XdStorageSqlCriterionOperator getOperator() {
        return operator;
    }

    public Object[] getValues() {
        return values;
    }

    @Override
    public String build(final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper,
                         final XdStorageClassInfo clInfo, final XdStorageSearchIndex index, final String fieldsTable,
                         final XdStorageObjectField primaryField) {

        final StringBuilder builder = new StringBuilder();

        // TODO create index for search fields table (field_name, value_class_name)
        builder.append(fieldsTable).append(".field_name = '").append(primaryField.getName()).append("' AND ");
        builder.append(fieldsTable).append(".value_class_name = '")
                .append(helper.simpleTypeValueToString(primaryField.field.getType())).append("' AND ");

        operator.place(builder, fieldsTable + ".value_as_string", helper, values);

        return builder.toString();
    }
}
