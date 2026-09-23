package org.flib.xdstorage.object;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageObject.
 */
public class XdStorageObjectTest {

    private XdStorageObject xdObject;

    @BeforeEach
    public void setUp() {
        xdObject = new XdStorageObject();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testGettersAndSetters_HappyPath() {
        Map<String, Object> props = new HashMap<>();
        props.put("username", "admin");

        xdObject.setType(String.class);
        xdObject.setProperties(props);

        assertEquals(String.class, xdObject.getType());
        assertSame(props, xdObject.getProperties());
        assertEquals("admin", xdObject.getProperty("username"));
    }

    @Test
    public void testSetProperty_WithLazyInitialization_ShouldCreateMap() {
        // Изначально мапа свойств равна null
        assertNull(xdObject.getProperties());

        // Метод setProperty обязан лениво проинициализировать HashMap
        xdObject.setProperty("age", 25);

        assertNotNull(xdObject.getProperties());
        assertEquals(25, xdObject.getProperty("age"));
    }

    @Test
    public void testEqualsAndHashCode_WithIdenticalProperties_ShouldBeEqual() {
        XdStorageObject obj1 = new XdStorageObject();
        obj1.setType(Integer.class);
        obj1.setProperty("val", 100);

        XdStorageObject obj2 = new XdStorageObject();
        obj2.setType(Integer.class);
        obj2.setProperty("val", 100);

        assertEquals(obj1, obj2);
        assertEquals(obj1.hashCode(), obj2.hashCode());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testGetProperty_WhenPropertiesMapIsNull_ShouldThrowNullPointerException() {
        // Граничное условие: мапа свойств не инициализирована.
        // Прямой вызов properties.get(name) обязан выбросить NullPointerException.
        assertThrows(NullPointerException.class, () -> {
            xdObject.getProperty("any_key");
        });
    }

    @Test
    public void testEquals_WithNullAndForeignObject_ShouldReturnFalseSafely() {
        xdObject.setType(Object.class);

        assertNotEquals(null, xdObject);
        assertNotEquals("just_a_string", xdObject);
    }
}
