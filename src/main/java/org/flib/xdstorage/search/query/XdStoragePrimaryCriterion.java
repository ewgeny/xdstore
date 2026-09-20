package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public class XdStoragePrimaryCriterion implements IXdStoragePrimaryCriterion {

    private IXdStorageCriterionOperator operator;

    private Object value;

    public XdStoragePrimaryCriterion(final IXdStorageCriterionOperator operator, final Object value) {
        this.operator = operator;
        this.value = value;
    }

    @Override
    public IXdStorageCriterionOperator getOperator() {
        return operator;
    }

    @Override
    public Object getValue() {
        return value;
    }

    public boolean passed(final XdStorageSearchIndexRecord record) {
        return operator.passed(record.getPrimaryIndexValue(), value);
    }
}
