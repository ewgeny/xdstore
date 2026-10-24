package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

/** A criterion on an index's PRIMARY value — the first declared index field, stored as
 * the B+tree key. The engine evaluates primary criteria on the ordered walk itself
 * (early termination for range operators with a LIMIT), before the secondary criteria. */
public interface IXdStoragePrimaryCriterion {

    /**
     * The operator applied to the primary value.
     *
     * @return one of the {@link IXdStorageCriterionOperator} constants
     */
    IXdStorageCriterionOperator getOperator();

    /**
     * The criterion value the operator compares the primary value with
     * (a {@link XdStorageRange} for {@code BETWEEN}; a collection/array for {@code IN}).
     *
     * @return the criterion value (may be {@code null} for the null tests)
     */
    Object getValue();

    /**
     * Evaluates this criterion against an index record's primary value.
     *
     * @param record the index record being filtered
     * @return {@code true} when the record passes
     */
    boolean passed(XdStorageSearchIndexRecord record);
}
