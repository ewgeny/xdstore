package org.flib.xdstorage.index;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class XdStorageIndexResourceCache {

    private final int fragmentSize;

    /**
     * objectId, resourceId
     */
    private Map<Object, Object> index = new ConcurrentHashMap<>();

    /**
     * resourceId, countObjects
     */
    private Map<Object, AtomicLong> counters = new HashMap<>();

    public XdStorageIndexResourceCache(final int fragmentSize) {
        this.fragmentSize = fragmentSize;
    }

    public boolean isClear() {
        return index.isEmpty();
    }

    public Collection<Object> getResourcesIds() {
        synchronized (counters) {
            return new ArrayList<>(counters.keySet());
        }
    }

    public Object getResourceId(final Object objectId) {
        return index.get(objectId);
    }

    public Object getFreeResourceId() {
        synchronized (counters) {
            for (final Map.Entry<Object, AtomicLong> record : counters.entrySet()) {
                if (record.getValue().longValue() < fragmentSize)
                    return record.getKey();
            }
        }
        return null;
    }

    public void insertRecord(final Object objectId, final Object resourceId) {
        index.put(objectId, resourceId);
        synchronized (counters) {
            AtomicLong count = counters.get(resourceId);
            if (count == null) {
                counters.putIfAbsent(resourceId, new AtomicLong(0));
                count = counters.get(resourceId);
            }
            count.incrementAndGet();
        }
    }

    public void deleteRecord(final Object objectId) {
        final Object resourceId = index.get(objectId);
        synchronized (counters) {
            final AtomicLong count = counters.get(resourceId);
            if (count.decrementAndGet() == 0) {
                index.remove(objectId);
            }
        }
    }
}
