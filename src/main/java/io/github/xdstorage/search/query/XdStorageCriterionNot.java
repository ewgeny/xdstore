package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

/** Negation of a sub-criterion: passes exactly when the wrapped criterion fails. */
public class XdStorageCriterionNot implements IXdStorageCriterion {

    private final IXdStorageCriterion criterion;

    /**
     * Builds the negation.
     *
     * @param criterion the criterion to negate (never {@code null})
     *
     * <p>Negation never converts failures into passes: a criterion that fails loudly
     * still fails loudly under NOT.
     */
    public XdStorageCriterionNot(final IXdStorageCriterion criterion) {
        this.criterion = criterion;
    }

    @Override
    public boolean passed(XdStorageSearchIndexRecord record) {
        return !criterion.passed(record);
    }
}
