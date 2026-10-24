package io.github.xdstorage.search.query;

import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import io.github.xdstorage.utils.XdStoragePair;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A search query: a list of (primary criterion, secondary criterion) pairs OR-ed
 * together, plus optional result shaping (ORDER BY primary, LIMIT/OFFSET — FP-1b).
 *
 * <p>Semantics: a record matches when its primary criterion passes AND its secondary
 * criterion passes; the pairs are alternatives — any matching pair includes the record.
 * A query with no criteria matches every record of the index. Instances are safe to
 * share across threads once built (the criteria list is a copy-on-write list); building
 * is single-threaded.
 *
 * <p>Shaping: {@link #orderByPrimary(boolean)} orders by the index's PRIMARY value
 * (native leaf-chain order on the ordered walk; an in-snapshot sort on scan plans);
 * {@link #limit(long)}/{@link #offset(long)} paginate. {@code count(...)} ignores the
 * shaping entirely (SQL COUNT semantics). */
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

    /** An empty query: no criteria (matches everything), no shaping. */
    public XdStorageSearchQuery() {
        this.criterions = new CopyOnWriteArrayList<>();
    }

    /**
     * A query with one OR-alternative.
     *
     * @param primary   the primary criterion (against the index's first declared field)
     * @param criterion the secondary criterion (against any indexed field)
     */
    public XdStorageSearchQuery(final IXdStoragePrimaryCriterion primary, final IXdStorageCriterion criterion) {
        this();
        criterions.add(new XdStoragePair<>(primary, criterion));
    }

    /** Appends an OR-alternative: records passing this (primary AND secondary) pair
     * are included alongside the existing alternatives.
     *
     * @param primary   the primary criterion of the alternative
     * @param criterion the secondary criterion of the alternative
     */
    public void or(final IXdStoragePrimaryCriterion primary, final IXdStorageCriterion criterion) {
        criterions.add(new XdStoragePair<>(primary, criterion));
    }

    /** The live OR-alternative list of (primary, secondary) pairs — thread-safe to read
     * while others append.
     *
     * @return the copy-on-write list of criterion pairs
     */
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
     *
     * @param limit maximum number of objects in the result (must be >= 0)
     * @return this query, for call chaining
     * @throws XdStorageRuntimeException when {@code limit < 0}
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
    /**
     * FP-1b: OFFSET — number of leading matches to skip after ordering. Zero is the
     * default (nothing skipped); a negative argument fails loudly right here. Combined
     * with {@link #limit(long)} this is the pagination window.
     *
     * @param offset number of matches to skip (must be >= 0)
     * @return this query, for call chaining
     * @throws XdStorageRuntimeException when {@code offset < 0}
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
    /** Whether any shaping (ORDER BY / LIMIT / OFFSET) is set — the engine routes shaped
     * queries through the FP-1b path.
     *
     * @return {@code true} when at least one shaping knob is set
     */
    public boolean hasShaping() {
        return orderByPrimary || limit >= 0 || offset >= 0;
    }

    /** Whether ORDER BY on the primary value is requested.
     *
     * @return {@code true} when {@link #orderByPrimary(boolean)} was called
     */
    public boolean isOrderByPrimary() {
        return orderByPrimary;
    }

    /** The ORDER BY direction.
     *
     * @return {@code false} for ASC (default), {@code true} for DESC
     */
    public boolean isOrderByPrimaryDescending() {
        return orderByPrimaryDescending;
    }

    /** FP-1b: LIMIT value, or -1 when unset (unbounded).
     *
     * @return the limit, or -1 when unset
     */
    public long getLimit() {
        return limit;
    }

    /** FP-1b: OFFSET value, or -1 when unset (treated as 0).
     *
     * @return the offset, or -1 when unset
     */
    public long getOffset() {
        return offset;
    }

    /** FP-1b: effective offset — the OFFSET the engine applies (unset means 0).
     *
     * @return the offset clamped to its effective value
     */
    public long effectiveOffset() {
        return offset < 0 ? 0 : offset;
    }
}
