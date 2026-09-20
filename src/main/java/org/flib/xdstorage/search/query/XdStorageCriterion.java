package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public class XdStorageCriterion implements IXdStorageCriterion {

    public final String fieldName;

    public final IXdStorageCriterionOperator operator;

    public final Object value;

    public XdStorageCriterion(final String fieldName, final IXdStorageCriterionOperator operator) {
        this(fieldName, operator, null);
    }

    public XdStorageCriterion(final String fieldName, final IXdStorageCriterionOperator operator, final Object value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    public boolean passed(final XdStorageSearchIndexRecord record) {
        return operator.passed(record, fieldName, value);
    }
}
