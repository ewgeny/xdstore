package org.flib.xdstorage.sqlstorage.fkresource;

import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки иммутабельных инвариантов класса XdStorageSQLCrossDatasourceFk.
 */
public class XdStorageSQLCrossDatasourceFkTest {

    @Test
    public void testConstructorAndGetters_HappyPath() {
        XdStorageSQLResourceId mockParentResId = mock(XdStorageSQLResourceId.class);
        XdStorageSQLResourceId mockChildResId = mock(XdStorageSQLResourceId.class);
        Object parentObj = new Object();
        Object childObj = new Object();

        // Инициализируем иммутабельный дескриптор связи
        XdStorageSQLCrossDatasourceFk fk = new XdStorageSQLCrossDatasourceFk(
                mockParentResId, parentObj, mockChildResId, childObj
        );

        // Проверяем сохранность ссылок
        assertSame(mockParentResId, fk.getParentResourceId());
        assertSame(parentObj, fk.getParentObject());
        assertSame(mockChildResId, fk.getChildResourceId());
        assertSame(childObj, fk.getChildObject());
    }
}
