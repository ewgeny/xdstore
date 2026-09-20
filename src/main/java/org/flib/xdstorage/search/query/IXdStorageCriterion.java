package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

public interface IXdStorageCriterion {

    boolean passed(XdStorageSearchIndexRecord record);
}
