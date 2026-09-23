package org.flib.xdstorage.observing;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageAbstractIdObserver.
 */
public class XdStorageAbstractIdObserverTest {

    // Локальная конкретная реализация абстрактного класса для тестирования
    private static class TestIdObserver extends XdStorageAbstractIdObserver {
        private boolean notified = false;

        @Override
        public void onNewIdIsSet(IXdStorageIdObservableWrapper wrapper, Object id) {
            this.notified = true;
        }
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testObserveIfPossible_WithValidWrapper_ShouldRegisterObserver() {
        TestIdObserver observer = new TestIdObserver();
        IXdStorageIdObservableWrapper mockWrapper = mock(IXdStorageIdObservableWrapper.class);

        // Передаем объект, который имплементирует нужный маркерный интерфейс
        observer.observeIfPossible(mockWrapper);

        // Проверяем, что обсерватор успешно навесился на прокси-обертку СУБД
        verify(mockWrapper, times(1)).addObserver(observer);
    }

    @Test
    public void testObserveIfPossible_WithPlainObject_ShouldDoNothingSafely() {
        TestIdObserver observer = new TestIdObserver();
        Object plainObject = new Object(); // Обычный класс без интерфейса IXdStorageIdObservableWrapper

        // Если объект не является прокси-оберткой, метод должен пропустить его без ошибок
        assertDoesNotThrow(() -> observer.observeIfPossible(plainObject));
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testObserveIfPossible_WithNull_ShouldThrowXdStorageRuntimeException() {
        TestIdObserver observer = new TestIdObserver();

        // Граничное условие: передача null в обсерватор
        XdStorageRuntimeException exception = assertThrows(XdStorageRuntimeException.class, () -> {
            observer.observeIfPossible(null);
        });

        // Проверяем каноничное fail-fast сообщение об ошибке ядра
        assertEquals("cannot observe for null object", exception.getMessage());
    }
}
