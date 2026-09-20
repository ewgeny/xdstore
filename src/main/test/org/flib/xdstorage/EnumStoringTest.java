package org.flib.xdstorage;

import org.flib.xdstorage.entities.map.EnumFieldObject;
import org.flib.xdstorage.entities.map.EnumMapKeyObject;
import org.flib.xdstorage.entities.map.TestEnum;
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

public class EnumStoringTest {

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
    public void saveAndLoadEnumValues() {
        clear(EnumFieldObject.class);

        EnumFieldObject object = new EnumFieldObject();
        object.setValue1(TestEnum.VALUE1);
        object.setValue2(TestEnum.VALUE2);

        IXdStorageTransaction tx = storage.beginTransaction();
        try {
            storage.save(object);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        Collection<EnumFieldObject> result;

        tx = storage.beginTransaction();
        try {
            result = storage.load(EnumFieldObject.class);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        assertEquals(1, result.size());
        Iterator<EnumFieldObject> it = result.iterator();
        assertTrue(it.hasNext());

        EnumFieldObject resultObject = it.next();
        assertNotNull(resultObject);
        assertEquals(object.getId(), resultObject.getId());
        assertEquals(object.getValue1(), resultObject.getValue1());
        assertEquals(object.getValue2(), resultObject.getValue2());

        tx = storage.beginTransaction();
        try {
            storage.delete(resultObject);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }
    }

    @Test
    public void saveAndLoadMapEnumKey() {
        clear(EnumMapKeyObject.class);

        EnumMapKeyObject object = new EnumMapKeyObject();
        Map<TestEnum, Object> map = new HashMap<>();
        map.put(TestEnum.VALUE1, "value1");
        map.put(TestEnum.VALUE2, "value2");
        object.setMap(map);
        EnumFieldObject fieldObject = new EnumFieldObject();
        fieldObject.setValue1(TestEnum.VALUE1);
        object.setFieldObject(fieldObject);

        IXdStorageTransaction tx = storage.beginTransaction();
        try {
            storage.save(object);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        Collection<EnumMapKeyObject> result;

        tx = storage.beginTransaction();
        try {
            result = storage.load(EnumMapKeyObject.class);

            tx.commit();
        } catch (XdStorageConnectionException | XdStorageException e) {
            tx.rollback();

            throw new RuntimeException(e);
        }

        assertEquals(1, result.size());
        Iterator<EnumMapKeyObject> it = result.iterator();
        assertTrue(it.hasNext());

        EnumMapKeyObject resultObject = it.next();
        assertNotNull(resultObject);
        assertEquals(object.getMap().get(TestEnum.VALUE1), "value1");
        assertEquals(object.getMap().get(TestEnum.VALUE2), "value2");

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
