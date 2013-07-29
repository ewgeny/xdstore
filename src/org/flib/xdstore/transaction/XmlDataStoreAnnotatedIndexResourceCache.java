package org.flib.xdstore.transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class XmlDataStoreAnnotatedIndexResourceCache {

	private final int           fragmentSize;

	/**
	 * objectId, resourceId
	 */
	private Map<Object, String> index    = new HashMap<Object, String>();

	/**
	 * resourceId, countObjects
	 */
	private Map<String, Long>   counters = new HashMap<String, Long>();

	public XmlDataStoreAnnotatedIndexResourceCache(final int fragmentSize) {
		this.fragmentSize = fragmentSize;
	}

	public synchronized boolean isClear() {
		return index.isEmpty();
	}

	public synchronized Collection<String> getResourcesIds() {
		return new ArrayList<String>(counters.keySet());
	}

	public synchronized String getResourceId(final Object objectId) {
		return index.get(objectId);
	}

	public synchronized String getFreeResourceId() {
		for (final Map.Entry<String, Long> record : counters.entrySet()) {
			if (record.getValue().longValue() < fragmentSize)
				return record.getKey();
		}
		return null;
	}

	public synchronized void insertRecord(final Object objectId, final String resourceId) {
		index.put(objectId, resourceId);
		final Long count = counters.remove(resourceId);
		counters.put(resourceId, count == null ? Long.valueOf(1) : Long.valueOf(count.longValue() + 1));
	}

	public synchronized void deleteRecord(final Object objectId) {
		final String resourceId = index.remove(objectId);
		final Long count = counters.remove(resourceId);
		if (count > 1) {
			counters.put(resourceId, Long.valueOf(count.longValue() - 1));
		}
	}
}
