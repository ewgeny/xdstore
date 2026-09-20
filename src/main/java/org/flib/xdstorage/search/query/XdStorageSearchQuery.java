package org.flib.xdstorage.search.query;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.ArrayList;
import java.util.List;

public class XdStorageSearchQuery {

    private final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> criterions;

    public XdStorageSearchQuery() {
        criterions = new ArrayList<>();
    }

    public XdStorageSearchQuery(final IXdStoragePrimaryCriterion primary, final IXdStorageCriterion criterion) {
        this();

        criterions.add(new XdStoragePair<>(primary, criterion));
    }

    public void or(final IXdStoragePrimaryCriterion primary, final IXdStorageCriterion criterion) {
        criterions.add(new XdStoragePair<>(primary, criterion));
    }

    public List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> getCriterions() {
        return criterions;
    }
}
