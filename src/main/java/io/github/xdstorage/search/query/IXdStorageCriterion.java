package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

/** One node of a search query's criteria tree: answers whether an index record passes. */
public interface IXdStorageCriterion {

    /**
     * Evaluates this criterion against an index record.
     *
     * @param record the index record being filtered
     * @return {@code true} when the record passes
     */
    boolean passed(XdStorageSearchIndexRecord record);
}
