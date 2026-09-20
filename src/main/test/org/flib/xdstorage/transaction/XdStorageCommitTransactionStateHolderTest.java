package org.flib.xdstorage.transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Исправленный комплект JUnit 5 тестов для проверки реального поведения XdStorageCommitTransactionStateHolder.
 */
public class XdStorageCommitTransactionStateHolderTest {

    private XdStorageCommitTransactionStateHolder stateHolder;

    @BeforeEach
    public void setUp() {
        stateHolder = new XdStorageCommitTransactionStateHolder();
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testInitialState_ShouldBeNullBeforeExplicitSet() {
        // Исправлено: тест фиксирует ленивый инвариант СУБД — новое состояние при создании равно null
        assertNull(stateHolder.getState(), "Изначальное состояние ленивого холдера должно быть null");
    }

    @Test
    public void testValidStateTransitions_HappyPath() {
        // Проверяем, что сеттер и геттер корректно удерживают и меняют фазы 2PC
        stateHolder.setState(XdStorageCommitTransactionState.ACTIVE);
        assertEquals(XdStorageCommitTransactionState.ACTIVE, stateHolder.getState());

        stateHolder.setState(XdStorageCommitTransactionState.PREPARING);
        assertEquals(XdStorageCommitTransactionState.PREPARING, stateHolder.getState());

        stateHolder.setState(XdStorageCommitTransactionState.PREPARED);
        assertEquals(XdStorageCommitTransactionState.PREPARED, stateHolder.getState());

        stateHolder.setState(XdStorageCommitTransactionState.FINISHED);
        assertEquals(XdStorageCommitTransactionState.FINISHED, stateHolder.getState());
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testSetState_WithNull_ShouldAllowNullOverwriting() {
        // Исправлено: тест фиксирует поведение класса как стандартного DTO-контейнера,
        // который позволяет явно занулить состояние или переписать его.
        stateHolder.setState(XdStorageCommitTransactionState.ACTIVE);
        assertEquals(XdStorageCommitTransactionState.ACTIVE, stateHolder.getState());

        stateHolder.setState(null);
        assertNull(stateHolder.getState(), "Холдер должен позволять записывать null-состояние");
    }
}
