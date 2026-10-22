package io.github.xdstorage.serialization;

import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.XdStorageProvider;
import io.github.xdstorage.entities.map.ObjectsMapObject;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class MapStoringTest {

    @TempDir
    static java.nio.file.Path tempDir;

    private static IXdFileStorage storage = null;

    @BeforeAll
    public static void beforeClass() {
        // T-2 vintage migration: R-50 hygiene — unique per-run name and temp dir
        // (the class was silently dead under the JUnit4 vintage gap; it runs now)
        storage = XdStorageProvider.newOrGetFileStorage("map-storing-" + System.nanoTime(),
                tempDir.resolve("db").toString(), 250);
    }

    @AfterAll
    public static void afterClass() {
        storage.shutdown();
    }

    @Test
    public void testMapObjectsStore() {
        clear(ObjectsMapObject.class);

        ObjectsMapObject object = new ObjectsMapObject();
        Map<Object, Object> map = new HashMap<>();
        map.put("string 1", new Object());
        map.put("string 2", new Object());
        object.setMap(map);

        IXdStorageTransaction tx = storage.beginTransaction();
        try {
            storage.save(object);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        Collection<ObjectsMapObject> result;

        tx = storage.beginTransaction();
        try {
            result = storage.load(ObjectsMapObject.class);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        assertEquals(1, result.size());
        Iterator<ObjectsMapObject> it = result.iterator();
        assertTrue(it.hasNext());

        ObjectsMapObject resultObject = it.next();
        assertNotNull(resultObject);
        assertEquals(object.getId(), resultObject.getId());

        map = resultObject.getMap();
        assertEquals(2, map.size());

        tx = storage.beginTransaction();
        try {
            storage.delete(resultObject);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }
    }

    void clear(Class<?> cl) {
        IXdStorageTransaction tx = storage.beginTransaction();
        try {
            Collection<?> objects = storage.load(cl);
            storage.delete(objects);

            tx.commit();
        } catch (Throwable e) {
            tx.rollback();
        }
    }
}
