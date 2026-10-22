package io.github.xdstorage.search.query;

import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.utils.XdStoragePair;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XdStorageSearchQuery {

    // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ ЯДРА ПОИСКОВОЙ СИСТЕМЫ:
    // Переводим коллекцию критериев на CopyOnWriteArrayList. Это гарантирует
    // пуленепробиваемое Lock-Free чтение условий параллельными потоками СУБД через .stream(),
    // полностью исключая ConcurrentModificationException при динамическом расширении запроса через .or()!
    private final List<XdStoragePair<IXdStoragePrimaryCriterion, IXdStorageCriterion>> criterions;

    // FP-1b (docs/tasks/2.1.0/10): result shaping — ORDER BY on the PRIMARY indexed value,
    // LIMIT/OFFSET pagination. Defaults keep the legacy contract: no ordering, no pagination.
    private boolean orderByPrimary;
    private boolean orderByPrimaryDescending;
    private long limit = -1;
    private long offset = -1;

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

    /**
     * FP-1b: ORDER BY the index's PRIMARY value (the B+tree key). Ordering is the engine's
     * native leaf-chain order on the ordered walk; on plans without a native order (scan path)
     * the engine sorts within the transaction snapshot. Secondary-field ordering is explicitly
     * not supported: the search index orders by key trees, not arbitrary fields.
     *
     * @param descending false = ASC (default), true = DESC
     * @return this query, for call chaining
     */
    public XdStorageSearchQuery orderByPrimary(final boolean descending) {
        this.orderByPrimary = true;
        this.orderByPrimaryDescending = descending;
        return this;
    }

    /**
     * FP-1b: LIMIT — maximum number of objects in the result. Zero is legal (an empty page);
     * a negative argument fails loudly right here. On the ordered walk path the limit is pushed
     * down: the walk stops as soon as the page is complete.
     */
    public XdStorageSearchQuery limit(final long limit) {
        if (limit < 0) {
            throw new XdStorageRuntimeException("LIMIT must be >= 0, got " + limit
                    + " — negative pagination arguments fail loudly at query construction");
        }
        this.limit = limit;
        return this;
    }

    /**
     * FP-1b: OFFSET — number of leading matches to skip after ordering. Zero is the default;
     * an offset past the end yields an empty page; a negative argument fails loudly right here.
     * With LIMIT + ordered walks an OFFSET re-walks the skipped prefix — keyset pagination is
     * the follow-up if that cost matters at scale.
     */
    public XdStorageSearchQuery offset(final long offset) {
        if (offset < 0) {
            throw new XdStorageRuntimeException("OFFSET must be >= 0, got " + offset
                    + " — negative pagination arguments fail loudly at query construction");
        }
        this.offset = offset;
        return this;
    }

    /** FP-1b: true when any result shaping is requested (ORDER BY, LIMIT or OFFSET). */
    public boolean hasShaping() {
        return orderByPrimary || limit >= 0 || offset >= 0;
    }

    public boolean isOrderByPrimary() {
        return orderByPrimary;
    }

    public boolean isOrderByPrimaryDescending() {
        return orderByPrimaryDescending;
    }

    /** FP-1b: LIMIT value, or -1 when unset (unbounded). */
    public long getLimit() {
        return limit;
    }

    /** FP-1b: OFFSET value, or -1 when unset (treated as 0). */
    public long getOffset() {
        return offset;
    }

    /** FP-1b: effective offset — unset means 0. */
    public long effectiveOffset() {
        return offset < 0 ? 0 : offset;
    }
}
