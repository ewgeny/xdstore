package org.flib.xdstorage.sqlstorage.sql.processor;

import org.flib.xdstorage.sqlstorage.sql.IXdStorageSQLTypesHelper;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тест безопасности (Exploit Test) в рамках Поинта Б.
 * Подсвечивает критическую утечку дескрипторов PreparedStatement в исполняемом слое СУБД.
 */
public class XdStorageSQLCommandLeakTest {

    @Test
    public void testExecute_ShouldClosePreparedStatementToPreventResourceLeaks() throws SQLException {
        // Изолируем зависимости
        IXdStorageSQLConnectionProvider mockProvider = mock(IXdStorageSQLConnectionProvider.class);
        IXdStorageTransaction mockTx = mock(IXdStorageTransaction.class);
        IXdStorageSQLTypesHelper mockHelper = mock(IXdStorageSQLTypesHelper.class);

        Connection mockConnection = mock(Connection.class);
        PreparedStatement mockStatement = mock(PreparedStatement.class);

        String sampleQuery = "INSERT INTO users_table (id, name) VALUES (?, ?)";
        Object resId = "res_123";

        // Настраиваем цепочку вызовов JDBC драйвера
        when(mockProvider.getConnection(resId, mockTx)).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(sampleQuery)).thenReturn(mockStatement);

        XdStorageSQLCommand command = new XdStorageSQLCommand(1, resId, sampleQuery, mockHelper);
        command.addParameter("user_1");

        // Выполняем команду
        command.execute(mockProvider, mockTx);

        // ПРОВЕРКА УЯЗВИМОСТИ: Стейтмент ОБЯЗАН быть закрыт через .close() для предотвращения утечки курсоров в PostgreSQL.
        // Данный ассерт упадет, так как в текущей реализации XdStorageSQLCommand метод st.close() отсутствует!
        verify(mockStatement, times(1)).close();
    }
}
