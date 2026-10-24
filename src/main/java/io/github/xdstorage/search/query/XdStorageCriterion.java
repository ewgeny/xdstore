package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

/** A single-field criterion: {@code <field> <operator> <value>} evaluated against an
 * index record's stored field value. Immutable; shared freely between queries and
 * threads. */
public class XdStorageCriterion implements IXdStorageCriterion {

    /** The indexed field this criterion compares. */
    public final String fieldName;

    /** The comparison operator. */
    public final IXdStorageCriterionOperator operator;

    /** The criterion value (a {@link XdStorageRange} for BETWEEN; a collection/array for IN; may be null). */
    public final Object value;

    /**
     * A valueless criterion (the null tests).
     *
     * @param fieldName the indexed field to compare
     * @param operator  the operator (typically {@code IS_NULL}/{@code IS_NOT_NULL})
     */
    public XdStorageCriterion(final String fieldName, final IXdStorageCriterionOperator operator) {
        this(fieldName, operator, null);
    }

    /**
     * A criterion with a value.
     *
     * @param fieldName the indexed field to compare
     * @param operator  the comparison operator
     * @param value     the criterion value (see {@link #value})
     */
    public XdStorageCriterion(final String fieldName, final IXdStorageCriterionOperator operator, final Object value) {
        this.fieldName = fieldName;
        this.operator = operator;
        this.value = value;
    }

    /** Delegates to the operator with this criterion's field and value. */
    @Override
    public boolean passed(final XdStorageSearchIndexRecord record) {
        return operator.passed(record, fieldName, value);
    }
}
