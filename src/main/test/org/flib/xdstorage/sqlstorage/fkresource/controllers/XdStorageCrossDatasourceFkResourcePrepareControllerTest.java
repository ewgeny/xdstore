package org.flib.xdstorage.sqlstorage.fkresource.controllers;

import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceId;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 тесты для проверки базовых и граничных условий класса XdStorageCrossDatasourceFkResourcePrepareController.
 * Изолирует физическое JDBC соединение для проверки каскадного выполнения DDL-запросов СУБД.
 */
public class XdStorageCrossDatasourceFkResourcePrepareControllerTest {

    private XdStorageSQLResourceId mockResourceId;
    private XdStorageSQLResourceNamingService mockNamingService;
    private IXdStorageSQLTypesHelper mockHelper;
    private XdStorageClassInfo mockParentClassInfo;
    private XdStorageClassInfo mockChildClassInfo;

    private XdStorageCrossDatasourceFkResourcePrepareController prepareController;

    @BeforeEach
    public void setUp() {
        mockResourceId = mock(XdStorageSQLResourceId.class);
        mockNamingService = mock(XdStorageSQLResourceNamingService.class);
        mockHelper = mock(IXdStorageSQLTypesHelper.class);
        mockParentClassInfo = mock(XdStorageClassInfo.class);
        mockChildClassInfo = mock(XdStorageClassInfo.class);

        // Настраиваем базовые заглушки имен таблиц для предотвращения NPE при внутренней сборке фабрики
        when(mockParentClassInfo.getClazz()).thenReturn((Class) String.class);
        when(mockChildClassInfo.getClazz()).thenReturn((Class) Integer.class);

        prepareController = new XdStorageCrossDatasourceFkResourcePrepareController(
                mockResourceId, mockNamingService, mockHelper, mockParentClassInfo, mockChildClassInfo
        );
    }

    // === 1. ОБЫЧНЫЕ ТЕСТЫ (Счастливый путь) ===

    @Test
    public void testCreateTables_HappyPath_ShouldExecuteDdlQueries() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        when(mockConnection.createStatement()).thenReturn(mockStatement);

        // Попытка вызвать выполнение таблиц. Метод execute выполнит строковые запросы ddl,
        // если они предварительно собраны методом initQueries(). Проверим безопасный вызов.
        assertDoesNotThrow(() -> {
            prepareController.createTables(mockConnection, mockTx);
        });

        // Проверяем, что стейтмент был успешно запрошен у коннекта и закрыт/выполнен
        verify(mockConnection, times(1)).createStatement();
        verify(mockStatement, times(2)).execute(any()); // Вызовы для основной таблицы и таблицы старых состояний
    }

    // === 2. ТЕСТЫ НА ГРАНИЧНЫЕ УСЛОВИЯ ===

    @Test
    public void testCreateTables_WhenStatementThrowsException_ShouldForwardSQLException() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        when(mockConnection.createStatement()).thenReturn(mockStatement);
        // Граничное условие: Сбой синтаксиса базы данных на первом же DDL-запросе CREATE TABLE
        doThrow(new SQLException("Сбой синтаксиса PostgreSQL")).when(mockStatement).execute(any());

        // Метод createTables не оборачивает ошибки стейтмента в кастомные исключения, а пробрасывает SQLException наружу
        assertThrows(SQLException.class, () -> {
            prepareController.createTables(mockConnection, mockTx);
        });
    }

    @Test
    public void testCreateTables_WithNullConnection_ShouldThrowNullPointerException() {
        XdStorageTransaction mockTx = mock(XdStorageTransaction.class);

        // Граничное условие: Передача null вместо валидного JDBC соединения
        assertThrows(NullPointerException.class, () -> {
            prepareController.createTables(null, mockTx);
        });
    }
}
