package org.flib.xdstorage.sqlstorage.search.query;

import org.flib.xdstorage.search.XdStorageSearchIndex;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.sqlstorage.sql.builders.search.XdStorageSearchIndexTableName;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.List;

public class XdStorageSqlChildCriterion implements IXdStorageSqlCriterion {

    private final String fieldName;

    private final Class<?> childClass;

    private final String childFieldName;

    private final XdStorageSqlCriterionOperator operator;

    private final Object[] values;

    public XdStorageSqlChildCriterion(final String fieldName, final Class<?> childClass, final String childFieldName,
                                      final XdStorageSqlCriterionOperator operator, final Object... values) {
        this.fieldName = fieldName;
        this.childClass = childClass;
        this.childFieldName = childFieldName;
        this.operator = operator;
        this.values = values;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getChildFieldName() {
        return childFieldName;
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

        final String childrenFieldsTable = namingService.getSearchIndexTable(clInfo.getClazz(), index.getName()) + "_" + XdStorageSearchIndexTableName.OBJECTS_CHILDREN_FIELDS.getNameTableSuffix();
        final String tableAlias = "tbl" + joinTables.size();
        joinTables.add(new XdStoragePair<>(childrenFieldsTable, tableAlias));

        final XdStorageObjectField field = index.getChildFieldAccesors(childClass).get(childFieldName);

        final StringBuilder builder = new StringBuilder();

        // TODO create index for search fields table (child_field_name, field_name, value_class_name)
//        builder.append(childrenFieldsTable).append(".child_class_name = '")
//                .append(helper.simpleTypeValueToString(childClass.getClazz())).append("' AND ");
        builder.append(tableAlias).append(".child_field_name = '").append(fieldName).append("' AND ");

        builder.append(tableAlias).append(".field_name = '").append(field.getName()).append("' AND ");
        builder.append(tableAlias).append(".value_class_name = '")
                .append(helper.simpleTypeValueToString(field.field.getType())).append("' AND ");

        operator.place(builder, tableAlias + ".value_as_string", helper, values);

        return builder.toString();
    }
}
