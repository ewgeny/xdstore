package io.github.xdstorage.index;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Оптимизированный Lock-Free кэш фрагментации индексов (Поинт Г).
 * Избавлен от synchronized блоков для обеспечения пиковой пропускной способности.
 */
public class XdStorageIndexResourceCache {

    private final int fragmentSize;
    private final Map<Object, Object> index = new ConcurrentHashMap<>();
    private final Map<Object, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * Creates a lock-free index cache.
     *
     * @param fragmentSize the record capacity of one data fragment — a fragment whose
     *                     record counter stays below it is treated as free for the next
     *                     insert
     */
    public XdStorageIndexResourceCache(final int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    /**
     * Whether the cache holds no id {@code ->} fragment mappings at all.
     *
     * @return {@code true} when the index part is empty
     */
    public boolean isClear() {
        return index.isEmpty();
    }

    /**
     * All data-fragment ids currently known to the cache, as a snapshot.
     *
     * @return an immutable-in-effect copy of the known fragment ids (never {@code null})
     */
    public Collection<Object> getResourcesIds() {
        // На чтение отдаем моментальный снимок ключей, не блокируя воркеров
        return new ArrayList<>(counters.keySet());
    }

    /**
     * Looks up the data fragment mapped to the given object id.
     *
     * @param objectId the object id to look up
     * @return the mapped data-fragment resource id, or {@code null} when unknown
     */
    public Object getResourceId(final Object objectId) {
        return index.get(objectId);
    }

    /**
     * Finds a data fragment that still has free capacity (its record counter is below the
     * fragment size) for the next insert.
     *
     * @return the id of a not-yet-full fragment, or {@code null} when every known fragment
     *         is full (or the cache is empty)
     */
    public Object getFreeResourceId() {
        // Читаем мапу параллельно без synchronized!
        for (final Map.Entry<Object, AtomicLong> record : counters.entrySet()) {
            if (record.getValue().get() < fragmentSize) {
                return record.getKey();
            }
        }
        return null;
    }

    /**
     * Registers the id {@code ->} fragment mapping and increments the target fragment's
     * record counter (lazily creating the counter).
     *
     * @param objectId   the object id being indexed
     * @param resourceId the data-fragment resource id the object's record lives in
     */
    public void insertRecord(final Object objectId, final Object resourceId) {
        index.put(objectId, resourceId);

        // Ленивая атомарная инициализация счетчиков фрагментов
        AtomicLong count = counters.get(resourceId);
        if (count == null) {
            counters.putIfAbsent(resourceId, new AtomicLong(0));
            count = counters.get(resourceId);
        }
        count.incrementAndGet();
    }

    /**
     * Unconditionally removes the object's id {@code ->} fragment mapping and decrements
     * the fragment's record counter, dropping the counter once it reaches zero (so emptied
     * fragments do not leak stale descriptors).
     *
     * @param objectId the object id whose mapping is removed (no-op when unknown)
     */
    public void deleteRecord(final Object objectId) {
        // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ СУБД: Вырезаем указатель текущего объекта из мапы index
        // БЕЗУСЛОВНО при каждом вызове удаления! Прежняя логика удаляла связь только если
        // счетчик падал до нуля, что приводило к утечкам "призраков" указателей в оперативной памяти.
        final Object resourceId = index.remove(objectId);

        if (resourceId != null) {
            final AtomicLong count = counters.get(resourceId);
            if (count != null) {
                // Если счетчик фрагментов действительно опустел, безопасно удаляем его дескриптор
                if (count.decrementAndGet() <= 0) {
                    counters.remove(resourceId);
                }
            }
        }
    }

}
