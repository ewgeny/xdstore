package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

public interface IXdStorageCriterion {

    boolean passed(XdStorageSearchIndexRecord record);
}
