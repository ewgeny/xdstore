package io.github.xdstorage.resource;

import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageReferenceInvisibleException;
import io.github.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * R-58: классификация отказа by-reference загрузки.
 * 1. Запись СУЩЕСТВУЕТ, но невидима в снапшоте читателя (чужая незакоммиченная
 *    вставка) — штатный ответ MVCC: XdStorageReferenceInvisibleException
 *    ("элемент не входит в видимую версию графа"). Гидрация коллекций
 *    сгенерированных врапперов и load(Collection, tx) пропускают такие элементы.
 * 2. Записи нет вообще — реальная висячая ссылка: громкий XdStorageException.
 * До R-58 оба случая бросали один и тот же XdStorageException, и гидрация
 * коллекций падала целиком, убивая транзакцию читателя.
 */
public class XdStorageInvisibleReferenceTest extends AbstractCacheResourceTest {

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        // Внутренний контур вставки вызывает idField.get(null) для отложенных оберток —
        // базовый стаб падает на null; делаем его null-безопасным
        doAnswer(invocation -> {
            final Object arg = invocation.getArgument(0);
            return arg == null ? null : (long) arg.hashCode();
        }).when(mockIdField).get(any());
    }

    @Test
    @DisplayName("Чужая незакоммиченная вставка — XdStorageReferenceInvisibleException (MVCC, не ошибка)")
    public void uncommittedForeignInsertIsInvisibleNotDangling() throws Exception {
        final XdStorageTransaction writer = mockTransaction("tx-writer");
        final XdStorageTransaction reader = mockTransaction("tx-reader");

        final TestEntity phantom = new TestEntity("phantom-156");
        resourceCache.insert(phantom, writer);

        final XdStorageException thrown = assertThrows(XdStorageException.class,
                () -> resourceCache.readByReference(phantom, reader));

        assertTrue(thrown instanceof XdStorageReferenceInvisibleException,
                "Невидимость в снапшоте обязана классифицироваться отдельным MVCC-исключением, было: "
                        + thrown.getClass().getSimpleName());
        assertTrue(thrown.getMessage().contains("not visible in the reader snapshot"),
                "Сообщение обязано называть невидимость снапшота");
    }

    @Test
    @DisplayName("Записи нет вообще — реальная висячая ссылка, громкий XdStorageException")
    public void missingRecordIsDanglingNotInvisible() throws Exception {
        final XdStorageTransaction reader = mockTransaction("tx-reader");

        final XdStorageException thrown = assertThrows(XdStorageException.class,
                () -> resourceCache.readByReference(new TestEntity("never-saved"), reader));

        assertFalse(thrown instanceof XdStorageReferenceInvisibleException,
                "Отсутствие записи — НЕ невидимость снапшота, а реальная висячая ссылка");
    }
}
