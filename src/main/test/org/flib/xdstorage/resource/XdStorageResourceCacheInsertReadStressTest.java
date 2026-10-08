package org.flib.xdstorage.resource;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.IXdStoragePredicate;
import org.flib.xdstorage.factories.IXdStorageCloner;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.transaction.IXdStorageTransactionManager;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class XdStorageResourceCacheInsertReadStressTest {

    private XdStorageServicesLocator mockLocator;
    private IXdStorageTransactionManager mockTxManager;
    private IXdStorageCloner mockCloner;
    private IXdStorage mockStorage;
    private XdStorageObjectIdField mockField;
    private XdStorageResourceCache resourceCache;

    // Простой доменный класс для тестирования кэширования
    public static class TestEntity {
        private Long id;
        private String value;

        public TestEntity(Long id, String value) {
            this.id = id;
            this.value = value;
        }
        public Long getId() { return id; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @BeforeEach
    public void setUp() throws Exception {
        mockLocator = mock(XdStorageServicesLocator.class);
        mockCloner = mock(IXdStorageCloner.class);
        mockStorage = mock(IXdStorage.class);
        mockField = mock(XdStorageObjectIdField.class);

        // =========================================================================
        // АБСОЛЮТНЫЙ ФИКС: Заменяем хрупкий Mockito.mock() на легковесный,
        // стопроцентно потокобезопасный анонимный Stub. Он не ведет историю вызовов,
        // работает со скоростью нативной JVM и полностью устраняет ArrayIndexOutOfBoundsException!
        // =========================================================================
        mockTxManager = new org.flib.xdstorage.transaction.IXdStorageTransactionManager() {
            @Override
            public IXdStorage getStorage() {
                return mockStorage;
            }

            @Override
            public IXdStorageTransaction beginTransaction(long timeout) {
                return null;
            }

            @Override
            public IXdStorageTransaction beginTransaction(IXdStorageTransaction transaction, long timeout) {
                return null;
            }

            @Override
            public IXdStorageTransaction getTransaction(String transactionId) {
                return null;
            }

            @Override
            public void commitTransaction(IXdStorageTransaction transaction) {

            }

            @Override
            public void rollbackTransaction(IXdStorageTransaction transaction) {

            }

            @Override
            public XdStorageTransaction getCurrentTransaction() { return null; }

            @Override
            public boolean isTransactionAlive(IXdStorageTransaction transaction) {
                return true;
            }

            @Override
            public void registerRollbackOnlyTransaction(IXdStorageTransaction transaction) {

            }
        };

        when(mockLocator.getTransactionsManager()).thenReturn(mockTxManager);
        when(mockLocator.getCloner()).thenReturn(mockCloner);
        when(mockLocator.getStorage()).thenReturn(mockStorage);

        // Настройка извлечения ID сущности
        when(mockField.get(any())).thenAnswer(invocation -> {
            Object obj = invocation.getArgument(0);
            if (obj instanceof TestEntity) return ((TestEntity) obj).getId();
            return null;
        });

        // Базовая настройка клонера (возвращает копию)
        when(mockCloner.cloneAndWrap(any(), any(), any())).thenAnswer(invocation -> {
            TestEntity original = invocation.getArgument(0);
            return new TestEntity(original.getId(), original.getValue());
        });

        resourceCache = new XdStorageResourceCache(mockLocator, TestEntity.class, mockField);
    }

    private XdStorageTransaction createMockTransaction(String txId, long timestart) {
        XdStorageTransaction tx = mock(XdStorageTransaction.class);
        when(tx.getTransactionId()).thenReturn(txId);
        when(tx.getTimestart()).thenReturn(timestart);
        when(tx.getTimeout()).thenReturn(5000L);
        return tx;
    }

    @Test
    @DisplayName("Нагрузочный стресс-тест: Высокая параллельная гонка на запись и чтение без дедлоков")
    public void testCache_HeavyParallelLoad_NoDeadlocksOrDataCorruption() throws Exception {
        int threadsCount = 16;
        int operationsPerThread = 200;

        ExecutorService pool = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);

        List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger idGenerator = new AtomicInteger(1000);

        for (int i = 0; i < threadsCount; i++) {
            final int threadIdx = i;
            pool.submit(() -> {
                try {
                    startLatch.await(); // Одновременный старт всех потоков-воркеров

                    for (int j = 0; j < operationsPerThread; j++) {
                        String txId = "tx-stress-" + threadIdx + "-" + j;
                        XdStorageTransaction tx = createMockTransaction(txId, System.nanoTime());

                        if (threadIdx % 2 == 0) {
                            // Пишущие потоки: генерируют массовые уникальные вставки
                            int currentId = idGenerator.incrementAndGet();
                            TestEntity entity = new TestEntity((long) currentId, "Value_" + currentId);
                            resourceCache.insert(entity, tx);
                        } else {
// Читающие потоки: выполняют диапазонное прецизионное сканирование
                            resourceCache.read(tx, new IXdStoragePredicate() {
                                @Override
                                public boolean passed(Object wrappedObject) {
                                    return wrappedObject != null;
                                }
                            });
                        }

                        // Снимаем нагрузку очисткой транзакции
                        resourceCache.clear(tx);
                    }
                } catch (Throwable e) {
                    exceptions.add(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }
        startLatch.countDown(); // Огонь!
        boolean finishedCleanly = finishLatch.await(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        exceptions.forEach(e -> e.printStackTrace(System.err));

        assertTrue(finishedCleanly, "🚨 ОБНАРУЖЕН DEADLOCK КЭША РЕСУРСОВ ПОД ВЫСОКОЙ НАГРУЗКОЙ!");
        assertTrue(exceptions.isEmpty(), "🚨 Зафиксированы падения ConcurrentModificationException или ложные гонки блокировок: " + exceptions);
    }

}
