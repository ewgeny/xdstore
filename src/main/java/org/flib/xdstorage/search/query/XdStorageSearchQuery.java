package org.flib.xdstorage.search.query;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.utils.XdStoragePair;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XdStorageSearchQuery {

    // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ ЯДРА ПОИСКОВОЙ СИСТЕМЫ:
    // Переводим коллекцию критериев на CopyOnWriteArrayList. Это гарантирует
    // пуленепробиваемое Lock-Free чтение условий параллельными потоками СУБД через .stream(),
    // полностью исключая ConcurrentModificationException при динамическом расширении запроса через .or()!
    private final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> criterions;

    public XdStorageSearchQuery() {
        this.criterions = new CopyOnWriteArrayList<>();
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
