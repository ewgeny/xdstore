package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.List;

public class XdStorageSqlCriterion implements IXdStorageSqlCriterion {

    private final String fieldName;

    private final XdStorageSqlCriterionOperator operator;

    private final Object[] values;

    public XdStorageSqlCriterion(final String fieldName, final XdStorageSqlCriterionOperator operator, final Object... values) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.values = values;
    }

    public String getFieldName() {
        return fieldName;
    }

    public XdStorageSqlCriterionOperator getOperator() {
        return operator;
    }

    public Object[] getValues() {
        return values;
    }

    @Override
    public String build(final XdStorageSQLResourceNamingService namingService, final IXdStorageSQLTypesHelper helper,
                        final XdStorageClassInfo clInfo, final XdStorageSearchIndex index, final List<XdStoragePair<String, String>> joinTables) {

        final String fieldsTable = namingService.getSearchIndexTable(clInfo.getClazz(), index.getName()) + "_" + XdStorageSearchIndexTableName.OBJECTS_FIELDS.getNameTableSuffix();
        final String tableAlias = "tbl" + joinTables.size();
        joinTables.add(new XdStoragePair<>(fieldsTable, tableAlias));


        final XdStorageObjectField field = index.getFieldAccesors().get(fieldName);

        final StringBuilder builder = new StringBuilder();

        builder.append(tableAlias).append(".field_name = '").append(field.getName()).append("' AND ");
        builder.append(tableAlias).append(".value_class_name = '")
                .append(helper.simpleTypeValueToString(field.field.getType())).append("' AND ");

        operator.place(builder, tableAlias + ".value_as_string", helper, values);

        return builder.toString();
    }
}
