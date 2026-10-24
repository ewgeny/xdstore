package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

/** The standard implementation of {@link IXdStoragePrimaryCriterion}: operator +
 * value against the index's primary value (the B+tree key). */
public class XdStoragePrimaryCriterion implements IXdStoragePrimaryCriterion {

    /** The comparison operator. */
    private IXdStorageCriterionOperator operator;

    /** The criterion value (a {@link XdStorageRange} for BETWEEN; a collection/array for IN). */
    private Object value;

    /**
     * Builds the criterion.
     *
     * @param operator the comparison operator
     * @param value    the criterion value (may be {@code null} for the null tests)
     */
    public XdStoragePrimaryCriterion(final IXdStorageCriterionOperator operator, final Object value) {
        this.operator = operator;
        this.value = value;
    }

    /** The comparison operator. */
    @Override
    public IXdStorageCriterionOperator getOperator() {
        return operator;
    }

    /** The criterion value. */
    @Override
    public Object getValue() {
        return value;
    }

    /** Compares the record's primary index value with this criterion's value. */
    @Override
    public boolean passed(final XdStorageSearchIndexRecord record) {
        return operator.passed(record.getPrimaryIndexValue(), value);
    }
}
