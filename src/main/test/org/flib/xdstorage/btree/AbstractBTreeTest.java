package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.MockStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.MockTransaction;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractBTreeTest {

    protected IXdStorage storage;

    @BeforeEach
    public void setUp() {
        storage = new MockStorage();
    }

    // =========================================================================
    // АЛГОРИТМИЧЕСКОЕ ИСПРАВЛЕНИЕ ХЕЛПЕРА (Ликвидация таймаутов 0 мс):
    // Переопределяем метод getTimeout() через анонимный подкласс MockTransaction.
    // Прежний рефлексивный перехват поля падал в блоке catch, из-за чего стаб
    // возвращал дефолтный 0 мс, заставляя локер мгновенно падать при нагрузке.
    // Теперь ядро СУБД гарантированно получает 5000 мс лимита ожидания!
    // =========================================================================
    protected MockTransaction createTx(String id) {
        return new MockTransaction(id) {
            @Override
            public long getTimeout() {
                return 5000L; // Фиксируем промышленный таймаут в 5 секунд для всех ForkJoin воркеров
            }
        };
    }
}
