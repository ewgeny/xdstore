package org.flib.xdstorage.transaction;

import org.flib.xdstorage.resource.IXdStorageResourceObject;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XdStorage2PCDeadlockTest {

    @Test
    @DisplayName("Тест дедлоков: Ресурсы с одинаковым приоритетом должны сортироваться детерминированно по ID")
    void testResourceDeterministicOrdering() {
        // Создаем моки ресурсов с одинаковым приоритетом типов данных, но разными файловыми ID
        IXdStorageResourceObject<?> resA = mock(IXdStorageResourceObject.class);
        when(resA.getResourceId()).thenReturn("resource-A-file.xml");

        IXdStorageResourceObject<?> resB = mock(IXdStorageResourceObject.class);
        when(resB.getResourceId()).thenReturn("resource-B-file.xml");

        // Создаем мок внешнего класса транзакции, к которому привязаны внутренние объекты
        XdStorageTransaction mockTransaction = mock(XdStorageTransaction.class);

        // ИСПРАВЛЕНИЕ СИНТАКСИСА: Используем синтаксис instance.new ВнутреннийКласс(...) для создания объектов
        XdStorageTransaction.ComparableResourceObject compA =
                mockTransaction.new ComparableResourceObject(resA, 1000); // Приоритет DATA_RESOURCE_ORDER = 1000

        XdStorageTransaction.ComparableResourceObject compB =
                mockTransaction.new ComparableResourceObject(resB, 1000); // Тот же приоритет 1000

        // Выполняем сравнение
        int compareResult = compA.compareTo(compB);

        // Так как "resource-A-file.xml" лексикографически меньше, чем "resource-B-file.xml",
        // объект compA должен идти в очереди на коммит РАНЬШЕ (иметь меньшее или отрицательное значение при сравнении)
        assertTrue(compareResult < 0,
                "Детерминированная сортировка ресурсов по алфавиту нарушена! Риск возникновения Deadlock при 2PC.");

        // Проверяем обратное условие для полной уверенности
        int reverseCompareResult = compB.compareTo(compA);
        assertTrue(reverseCompareResult > 0,
                "Компаратор работает несимметрично.");
    }
}
