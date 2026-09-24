package org.flib.xdstorage.idgeneration;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageStringIdGenerator.
 */
public class XdStorageStringIdGeneratorTest {

    @Test
    public void testGenerate_HappyPath_ShouldReturnValidUniqueUuidStrings() {
        XdStorageStringIdGenerator generator = new XdStorageStringIdGenerator();
        IXdStorage mockStorage = mock(IXdStorage.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);

        // Генерируем два последовательных идентификатора
        Object id1 = generator.generate(String.class, mockStorage, mockTx);
        Object id2 = generator.generate(String.class, mockStorage, mockTx);

        assertNotNull(id1);
        assertNotNull(id2);
        assertTrue(id1 instanceof String);

        // Проверяем инвариант уникальности UUID и соответствие формату
        assertNotEquals(id1, id2);
        assertEquals(36, ((String) id1).length()); // Длина стандартного UUID строки
    }

    @Test
    public void testGenerate_WithNullArguments_ShouldStillWorkSafely() {
        XdStorageStringIdGenerator generator = new XdStorageStringIdGenerator();

        // Граничное условие: Так как генератор UUID является чистым Stateless,
        // передача null-параметров не должна приводить к сбоям рант-тайма
        assertDoesNotThrow(() -> {
            Object id = generator.generate(null, null, null);
            assertNotNull(id);
        });
    }
}
