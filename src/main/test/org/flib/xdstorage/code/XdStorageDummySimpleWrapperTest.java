package org.flib.xdstorage.code;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 тесты для проверки поведения Dummy-заглушки прокси-компилятора.
 */
public class XdStorageDummySimpleWrapperTest {

    @Test
    public void testDummyWrapper_HappyPathAndEdgeCases() {
        XdStorageDummySimpleWrapper dummy = new XdStorageDummySimpleWrapper();

        // Фиксируем инварианты пустой заглушки СУБД
        assertTrue(XdStorageDummySimpleWrapper.isDummySimpleWrapper());
        assertNull(dummy.getObjectId__());
        assertFalse(dummy.isReference__());

        // Проверяем, что вызовы блокировок на заглушке ничего не делают и не падают с NPE
        assertDoesNotThrow(() -> dummy.lock__());
        assertDoesNotThrow(() -> dummy.unlock__());
        assertDoesNotThrow(() -> dummy.setReference__(true));
    }
}
