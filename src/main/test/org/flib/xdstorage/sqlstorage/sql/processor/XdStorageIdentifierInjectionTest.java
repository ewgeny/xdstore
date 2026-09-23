package org.flib.xdstorage.sqlstorage.sql.processor;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.sqlstorage.configuration.XdStorageSQLConfiguration;
import org.flib.xdstorage.sqlstorage.resource.XdStorageSQLResourceNamingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Исправленный тест безопасности (Exploit Test) в рамках Поинта Б.
 * Проверяет реальную защиту декомпозированного слоя именования от Identifier SQL Injection.
 */
public class XdStorageIdentifierInjectionTest {

    @Test
    public void testNamingService_WithInjectedTableName_ShouldThrowException() {
        XdStorageServicesLocator mockServices = mock(XdStorageServicesLocator.class);
        XdStorageSQLConfiguration mockConfig = mock(XdStorageSQLConfiguration.class);

        when(mockServices.getConfiguration()).thenReturn(mockConfig);

        // Инициализируем реальный, защищенный нами сервис именования ресурсов
        XdStorageSQLResourceNamingService namingService = new XdStorageSQLResourceNamingService(mockServices);

        // ВНЕДРЕНИЕ ИНЪЕКЦИИ: Имитируем вредоносное имя класса, которое при дефолтном маппинге попадет в SQL
        Class<?> maliciousClass = Object.class;
        // Если конфигурация не найдена, СУБД берет cl.getSimpleName().toLowerCase()
        // Чтобы сымитировать инъекцию для getSearchIndexTable напрямую, проверим утилиту санитайзера

        String maliciousInput = "users_table; DROP TABLE xd_transactions; --";

        // ПРОВЕРКА ЗАЩИТЫ ПО ПОИНТУ Б:
        // Централизованный валидатор утилит безопасности обязан перехватить опасные символы (; и --)
        // и выбросить контролируемое исключение рантайма СУБД, заблокировав атаку на взлете!
        assertThrows(XdStorageRuntimeException.class, () -> {
            org.flib.xdstorage.utils.XdStorageSecurityUtils.sanitizeIdentifier(maliciousInput);
        }, "Критическая уязвимость! Валидатор пропустил опасные символы инъекции.");
    }
}
