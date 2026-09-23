package org.flib.xdstorage.index;

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

    public XdStorageIndexResourceCache(final int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    public boolean isClear() {
        return index.isEmpty();
    }

    public Collection<Object> getResourcesIds() {
        // На чтение отдаем моментальный снимок ключей, не блокируя воркеров
        return new ArrayList<>(counters.keySet());
    }

    public Object getResourceId(final Object objectId) {
        return index.get(objectId);
    }

    public Object getFreeResourceId() {
        // Читаем мапу параллельно без synchronized!
        for (final Map.Entry<Object, AtomicLong> record : counters.entrySet()) {
            if (record.getValue().get() < fragmentSize) {
                return record.getKey();
            }
        }
        return null;
    }

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

    public void deleteRecord(final Object objectId) {
        final Object resourceId = index.get(objectId);
        if (resourceId != null) {
            final AtomicLong count = counters.get(resourceId);
            if (count != null && count.decrementAndGet() == 0) {
                index.remove(objectId);
                counters.remove(resourceId);
            }
        }
    }
}
