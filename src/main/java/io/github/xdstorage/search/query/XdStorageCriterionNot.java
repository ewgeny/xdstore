package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

public class XdStorageCriterionNot implements IXdStorageCriterion {

    private final IXdStorageCriterion criterion;

    public XdStorageCriterionNot(final IXdStorageCriterion criterion) {
        this.criterion = criterion;
    }

    @Override
    public boolean passed(XdStorageSearchIndexRecord record) {
        return !criterion.passed(record);
    }
}
