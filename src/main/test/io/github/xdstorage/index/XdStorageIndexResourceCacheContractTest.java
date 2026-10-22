package io.github.xdstorage.index;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-3a (docs/tasks/2.1.0/06): the lock-free index-fragmentation cache contract
 * (io.github.xdstorage.index.XdStorageIndexResourceCache) — object→resourceId pointer map
 * with per-resourceId fragment counters. Pins: pointer insertion/lookup/removal, the
 * under-fragment pick for new records, descriptor removal when a fragment empties,
 * the snapshot semantics of getResourcesIds, and pointer-free concurrency.
 */
@DisplayName("T-3a: index resource cache contract")
public class XdStorageIndexResourceCacheContractTest {

    @Test
    @DisplayName("insert/lookup/delete round-trip; lookup of an unknown id is null")
    public void pointerRoundTrip() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(4);
        assertTrue(cache.isClear(), "a fresh cache is clear");

        cache.insertRecord("obj-1", "res-A");
        assertEquals("res-A", cache.getResourceId("obj-1"));
        assertNull(cache.getResourceId("missing"), "an unknown object has no pointer");

        cache.deleteRecord("obj-1");
        assertNull(cache.getResourceId("obj-1"), "the pointer is removed unconditionally on delete");
        assertTrue(cache.isClear());
    }

    @Test
    @DisplayName("delete of an unknown pointer is a no-op")
    public void deleteUnknownIsNoOp() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(4);
        assertDoesNotThrow(() -> cache.deleteRecord("ghost"));
        assertTrue(cache.isClear());
    }

    @Test
    @DisplayName("getFreeResourceId picks a resource whose fragment still has room")
    public void freeResourcePick() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(2);
        assertNull(cache.getFreeResourceId(), "no resources registered yet");

        cache.insertRecord("o1", "res-A");
        assertEquals("res-A", cache.getFreeResourceId(), "one slot of two is used — the fragment has room");

        cache.insertRecord("o2", "res-A"); // res-A full (fragment size 2)
        assertNull(cache.getFreeResourceId(), "the only fragment is full");

        cache.insertRecord("o3", "res-B");
        assertEquals("res-B", cache.getFreeResourceId(), "the full fragment is skipped, res-B has room");
        assertTrue(cache.getResourcesIds().contains("res-A"));
        assertTrue(cache.getResourcesIds().contains("res-B"));
    }

    @Test
    @DisplayName("all fragments full → getFreeResourceId returns null")
    public void allFragmentsFullReturnsNull() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(1);
        cache.insertRecord("o1", "res-A");
        assertNull(cache.getFreeResourceId(), "the only fragment is full");
    }

    @Test
    @DisplayName("emptying a fragment removes its counter descriptor")
    public void emptiedFragmentDescriptorRemoved() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(4);
        cache.insertRecord("o1", "res-A");
        cache.insertRecord("o2", "res-A");
        cache.deleteRecord("o1");
        assertFalse(cache.isClear(), "one pointer remains");
        cache.deleteRecord("o2");
        assertTrue(cache.isClear());
        assertTrue(cache.getResourcesIds().isEmpty(), "the emptied fragment's descriptor is gone");
        // a deleted descriptor frees the resource for the free-pick again
        cache.insertRecord("o3", "res-A");
        assertEquals("res-A", cache.getFreeResourceId());
    }

    @Test
    @DisplayName("getResourcesIds is a snapshot: mutating the cache does not mutate the returned collection")
    public void resourceIdsSnapshot() {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(4);
        cache.insertRecord("o1", "res-A");
        final java.util.Collection<Object> snapshot = cache.getResourcesIds();
        assertEquals(1, snapshot.size());
        snapshot.clear();
        assertEquals(1, cache.getResourcesIds().size(), "the cache is untouched by mutations of the snapshot");
    }

    @Test
    @DisplayName("concurrent insert/delete of distinct pointers keeps counters consistent")
    public void concurrentPointers() throws Exception {
        final XdStorageIndexResourceCache cache = new XdStorageIndexResourceCache(1000);
        final int threads = 8;
        final int perThread = 200;
        final CountDownLatch latch = new CountDownLatch(threads);
        final ConcurrentHashMap<String, Boolean> all = new ConcurrentHashMap<>();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            new Thread(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        final String obj = "o-" + (base + i);
                        cache.insertRecord(obj, "res-" + (base + i) / 50);
                        all.put(obj, Boolean.TRUE);
                        cache.deleteRecord(obj);
                        cache.insertRecord(obj, "res-" + (base + i) / 50);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        assertEquals(threads * perThread, all.size());
        final AtomicInteger pointers = new AtomicInteger();
        for (final Object id : all.keySet()) {
            if (cache.getResourceId(id) != null) {
                pointers.incrementAndGet();
            }
        }
        assertEquals(all.size(), pointers.get(), "no pointer lost under concurrency");
    }
}
