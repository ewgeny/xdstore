package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public class XdStorageChildCriterion implements IXdStorageCriterion {

    public final String childFieldName;

    public final Class<?> cl;

    public final String fieldName;

    public final IXdStorageChildCriterionOperator operator;

    public final Object value;

    public XdStorageChildCriterion(final String childFieldName, final Class<?> cl, final String fieldName, final IXdStorageChildCriterionOperator operator) {
        this(childFieldName, cl, fieldName, operator, null);
    }

    public XdStorageChildCriterion(final String childFieldName, final Class<?> cl, final String fieldName, final IXdStorageChildCriterionOperator operator, final Object value) {
        this.childFieldName = childFieldName;
        this.cl = cl;
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    public boolean passed(final XdStorageSearchIndexRecord record) {
        return operator.passed(record, childFieldName, cl, fieldName, value);
    }
}
