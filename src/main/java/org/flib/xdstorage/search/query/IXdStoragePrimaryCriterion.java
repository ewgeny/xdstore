package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public interface IXdStoragePrimaryCriterion {

    IXdStorageCriterionOperator getOperator();

    Object getValue();

    boolean passed(XdStorageSearchIndexRecord record);
}
