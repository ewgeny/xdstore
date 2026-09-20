package org.flib.xdstorage;

import org.flib.xdstorage.entities.map.ObjectsMapObject;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class MapStoringTest {

    private static IXdFileStorage storage = null;

    @BeforeClass
    public static void beforeClass() {
        storage = XdStorageProvider.newOrGetFileStorage("filetest", "./teststorage", 250);
    }

    @AfterClass
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
