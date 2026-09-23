package org.flib.xdstorage.object;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки контрактов наследования и равенства XdStorageIdentifiableObject.
 */
public class XdStorageIdentifiableObjectTest {

    @Test
    public void testGettersAndSetters_HappyPath() {
        XdStorageIdentifiableObject identifiableObject = new XdStorageIdentifiableObject();

        identifiableObject.setId("uuid_users_44");
        identifiableObject.setType(Object.class);

        assertEquals("uuid_users_44", identifiableObject.getId());
        assertEquals(Object.class, identifiableObject.getType());
    }

    @Test
    public void testEqualsAndHashCode_BasedOnIdAndSuperProperties() {
        XdStorageIdentifiableObject obj1 = new XdStorageIdentifiableObject();
        obj1.setId("id_1");
        obj1.setType(String.class);
        obj1.setProperty("name", "A");

        XdStorageIdentifiableObject obj2 = new XdStorageIdentifiableObject();
        obj2.setId("id_1");
        obj2.setType(String.class);
        obj2.setProperty("name", "A");

        XdStorageIdentifiableObject obj3 = new XdStorageIdentifiableObject();
        obj3.setId("id_2"); // Разные ID
        obj3.setType(String.class);
        obj3.setProperty("name", "A");

        assertEquals(obj1, obj2);
        assertEquals(obj1.hashCode(), obj2.hashCode());

        assertNotEquals(obj1, obj3);
    }

    @Test
    public void testEquals_WithNullFields_ShouldHandleSafely() {
        // Граничное условие: Идентификация объектов с незаполненными (null) первичными ключами
        XdStorageIdentifiableObject obj1 = new XdStorageIdentifiableObject();
        XdStorageIdentifiableObject obj2 = new XdStorageIdentifiableObject();

        // Сравнение двух null-ID объектов с пустыми свойствами должно сработать безопасно
        assertDoesNotThrow(() -> {
            assertEquals(obj1, obj2);
        });
    }
}
