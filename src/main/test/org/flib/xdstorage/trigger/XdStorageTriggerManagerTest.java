package org.flib.xdstorage.trigger;

import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий подсистемы триггеров СУБД.
 */
public class XdStorageTriggerManagerTest {

    private XdStorageTriggerManager triggerManager;
    private XdStorageTransaction mockTx;

    // Локальные шпионы для отслеживания вызовов абстрактных триггеров
    private static class TestInsertTrigger extends XdStorageAbstractInsertTrigger<String> {
        private boolean called = false;
        private String passedObject = null;

        @Override
        public Class<String> getClazz() { return String.class; }
        @Override
        public XdStorageObjectOperationType getType() { return XdStorageObjectOperationType.Insert; }

        @Override
        protected void perform(String object, IXdStorageTransaction transaction) {
            this.called = true;
            this.passedObject = object;
        }
    }

    private static class TestDeleteTrigger extends XdStorageAbstractDeleteTrigger<String> {
        private boolean called = false;
        private String passedObject = null;

        @Override
        public Class<String> getClazz() { return String.class; }
        @Override
        public XdStorageObjectOperationType getType() { return XdStorageObjectOperationType.Delete; }

        @Override
        protected void perform(String oldObject, IXdStorageTransaction transaction) {
            this.called = true;
            this.passedObject = oldObject;
        }
    }

    @BeforeEach
    public void setUp() {
        triggerManager = new XdStorageTriggerManager();
        mockTx = mock(XdStorageTransaction.class);
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testRegisterAndPerformInsertTrigger_HappyPath() {
        TestInsertTrigger insertTrigger = new TestInsertTrigger();
        triggerManager.registerTrigger(insertTrigger);

        Collection<XdStorageObjectChange> changes = new ArrayList<>();
        // Имитируем запись вставки строки "NewData" в лог изменений
        changes.add(new XdStorageObjectChange(XdStorageObjectOperationType.Insert, "id_1", null, "NewData"));

        triggerManager.performTriggers(mockTx, changes);

        // Проверяем, что менеджер триггеров распознал операцию и успешно вызвал наш insert-хук
        assertTrue(insertTrigger.called);
        assertEquals("NewData", insertTrigger.passedObject);
    }

    @Test
    public void testRegisterAndPerformDeleteTrigger_HappyPath() {
        TestDeleteTrigger deleteTrigger = new TestDeleteTrigger();
        triggerManager.registerTrigger(deleteTrigger);

        Collection<XdStorageObjectChange> changes = new ArrayList<>();
        // Immitate delete operation
        changes.add(new XdStorageObjectChange(XdStorageObjectOperationType.Delete, "id_2", "OldData", null));

        triggerManager.performTriggers(mockTx, changes);

        // Проверяем вызов хука удаления
        assertTrue(deleteTrigger.called);
        assertEquals("OldData", deleteTrigger.passedObject);
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testPerformTriggers_WithEmptyChanges_ShouldDoNothingSafely() {
        TestInsertTrigger insertTrigger = new TestInsertTrigger();
        triggerManager.registerTrigger(insertTrigger);

        Collection<XdStorageObjectChange> emptyChanges = new ArrayList<>();

        // Граничное условие: Список изменений пуст, ни один триггер не должен выстрелить
        assertDoesNotThrow(() -> triggerManager.performTriggers(mockTx, emptyChanges));
        assertFalse(insertTrigger.called);
    }

    @Test
    public void testPerformTriggers_WithNullCollection_ShouldThrowNullPointerException() {
        // Граничное условие: Передача null вместо коллекции изменений
        assertThrows(NullPointerException.class, () -> {
            triggerManager.performTriggers(mockTx, null);
        });
    }

    @Test
    public void testPerformTriggers_WithUnregisteredClass_ShouldIgnoreSafely() {
        TestInsertTrigger insertTrigger = new TestInsertTrigger();
        triggerManager.registerTrigger(insertTrigger);

        Collection<XdStorageObjectChange> changes = new ArrayList<>();
        // Передаем объект Integer (123) вместо ожидаемой триггером String.class
        changes.add(new XdStorageObjectChange(XdStorageObjectOperationType.Insert, "id_3", null, 123));

        // Менеджер триггеров должен безопасно проигнорировать этот тип данных, не дергая триггер для String
        assertDoesNotThrow(() -> triggerManager.performTriggers(mockTx, changes));
        assertFalse(insertTrigger.called);
    }
}
