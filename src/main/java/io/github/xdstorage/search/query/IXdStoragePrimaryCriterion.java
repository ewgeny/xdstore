package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

public interface IXdStoragePrimaryCriterion {

    IXdStorageCriterionOperator getOperator();

    Object getValue();

    boolean passed(XdStorageSearchIndexRecord record);
}
