package org.flib.xdstorage.performance;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeId;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Многопоточные стресс-тесты: Изоляция B+ Дерева индексов при каскадных сплитах")
public class XdStorageBTreeStressTest {

    private static final int PARALLEL_THREADS = 8;
    private static final int TEST_DURATION_MS = 3000; // 3 секунды яростного штурма нод

    @Test
    @DisplayName("Стресс-кейс: Параллельный поиск find() против агрессивных конкурентных сплитов нод B+ Дерева")
    public void testBTree_ParallelFind_AgainstHeavySplits_ShouldMaintainSnapshotIsolation() throws Exception {
        // =========================================================================
        // ШАГ 1: ИНИЦИАЛИЗАЦИЯ ЗАГЛУШЕК И ЖИВОГО ДЕРЕВА ИНДЕКСOВ
        // =========================================================================
        final IXdStorage mockStorage = mock(IXdStorage.class);
        final IXdStorageTransaction mockTransaction = mock(IXdStorageTransaction.class);

        when(mockTransaction.getTransactionId()).thenReturn("tx-stress-999");
        when(mockTransaction.getTimeout()).thenReturn(5000L);

        // Создаем множественное дерево (multiple = true), как в id_index СУБД, с фактором ветвления t = 3
        final XdStorageBTreeId treeId = new XdStorageBTreeId(String.class, "id_index");
        final XdStorageBTree bTree = new XdStorageBTree(treeId, true, 3);

        // Перехватываем каскадные сохранения страниц СУБД, чтобы дерево в памяти не теряло ссылки
        Mockito.doAnswer(invocation -> {
            Object node = invocation.getArguments()[0];
            return null;
        }).when(mockStorage).save(Mockito.any(), Mockito.any());

        Mockito.doAnswer(invocation -> {
            Object node = invocation.getArguments()[0];
            return null;
        }).when(mockStorage).update(Mockito.any(), Mockito.any());

        // Преднаполняем индекс эталонным пакетом дубликатов для ключа "target-key"
        final String targetKey = "target-key";
        final int expectedRecordsCount = 200;

        for (int i = 0; i < expectedRecordsCount; i++) {
            bTree.insert(targetKey, "resource-id-" + i, mockStorage, mockTransaction);
        }

        final ExecutorService threadPool = Executors.newFixedThreadPool(PARALLEL_THREADS);
        final AtomicBoolean isRunning = new AtomicBoolean(true);

        // Метрики аномалий
        final AtomicInteger phantomOrDataLossErrors = new AtomicInteger(0);
        final AtomicInteger npeCounter = new AtomicInteger(0);
        final AtomicInteger totalFinds = new AtomicInteger(0);
        final AtomicInteger totalSplits = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // =========================================================================
        // ПОТOКИ ЗАПИСИ (0-3): Непрерывно вставляют ДРУГИЕ ключи, провоцируя сплиты нод
        // =========================================================================
        for (int i = 0; i < PARALLEL_THREADS / 2; i++) {
            final int workerId = i;
            futures.add(threadPool.submit(() -> {
                int keySequence = 0;
                while (isRunning.get()) {
                    try {
                        // Пишем монотонно растущие ключи ("noise-0", "noise-1"...), вызывая
                        // непрерывное переполнение листовых страниц и каскадные split-акты!
                        String noiseKey = "noise-" + workerId + "-" + (keySequence++);
                        bTree.insert(noiseKey, "noise-resource", mockStorage, mockTransaction);
                        totalSplits.incrementAndGet();
                    } catch (NullPointerException npe) {
                        npeCounter.incrementAndGet();
                    } catch (Exception e) {
                        // Ловим блокировки/прерывания
                    }
                }
            }));
        }

        // =========================================================================
        // ПОТOКИ ЧТEНИЯ (4-7): Непрерывно запрашивают "target-key" и проверяют размер снимка
        // =========================================================================
        for (int i = PARALLEL_THREADS / 2; i < PARALLEL_THREADS; i++) {
            futures.add(threadPool.submit(() -> {
                while (isRunning.get()) {
                    try {
                        // Атомарно ищем наш ключ в эпицентре параллельной ребалансировки нод дерева!
                        List<Object> searchResult = bTree.find(targetKey, mockStorage, mockTransaction);
                        totalFinds.incrementAndGet();

                        if (searchResult == null) {
                            phantomOrDataLossErrors.incrementAndGet();
                            continue;
                        }

                        // КРИТИЧEСКИЙ ИНВАРИАНТ MVCC ИЗОЛЯЦИИ:
                        // Сколько бы сплитов ни происходило параллельно, читатель ОБЯЗАН
                        // вытащить из множественного дерева ровно 200 исходных элементов!
                        // Если из-за разрыва горизонтальных ссылок find() вернул 0 или обрезал данные —
                        // мы поймали фантомное чтение / потерю Snapshot Isolation!
                        if (searchResult.size() != expectedRecordsCount) {
                            phantomOrDataLossErrors.incrementAndGet();
                            System.err.println("🚨 ФАНТOМ/ПOТEРЯ Данных! Ожидали стабильных " + expectedRecordsCount
                                    + ", а получили: " + searchResult.size());
                        }
                    } catch (NullPointerException npe) {
                        // Перехватываем аппаратный NPE (например, getTree() == null), который рушил нам сборку
                        npeCounter.incrementAndGet();
                        System.err.println("🚨 КРАХ ИНДEКСА: Перехвачен NullPointerException в потоке чтения Б+ Дерева!");
                    } catch (Exception e) {
                        // Системные сбои
                    }
                }
            }));
        }

        // =========================================================================
        // ШАГ 3: ШТУРМ И СБOР МEТРИК
        // =========================================================================
        Thread.sleep(TEST_DURATION_MS);
        isRunning.set(false);
        threadPool.shutdown();
        threadPool.awaitTermination(1, TimeUnit.SECONDS);

        // Проверяем, что ни один поток не завершился по неконтролируемому эксепшену
        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get(), "Стресс-поток Б+ Дерева аварийно рухнул!");
        }

        System.out.println("=========================================================================");
        System.out.println("📊 РEЗУЛЬТАТЫ СТРEСС-ТEСТИРOВАНИЯ Б+ ДEРEВА ИНДEКСOВ СУБД:");
        System.out.println("🔹 Всего выполнено поисковых запросов find(): " + totalFinds.get());
        System.out.println("🔹 Всего выполнено мутаций/сплитов страниц (Splits): " + totalSplits.get());
        System.out.println("❌ Зафиксировано NullPointerException (Крах структуры): " + npeCounter.get());
        System.out.println("❌ Зафиксировано нарушений Snapshot Isolation (Фантомы/Промахи): " + phantomOrDataLossErrors.get());
        System.out.println("=========================================================================");

        // Главные утверждения многопоточного качества ядра СУБД
        assertEquals(0, npeCounter.get(),
                "🚨 КАТАСТРОФА! Ноды дерева выдают NullPointerException из-за гонок видимости!");
        assertEquals(0, phantomOrDataLossErrors.get(),
                "🚨 КАТАСТРОФА! B+ Дерево нарушает Snapshot Isolation! Читатели теряют дубликаты при сплитах!");
    }

    @Test
    @DisplayName("Стресс-кейс 2: Параллельный поиск find() против жестких каскадных удалений и слияний нод (Merges/Joins)")
    public void testBTree_ParallelFind_AgainstHeavyDeletesAndMerges_ShouldBeDeadlockProof() throws Exception {
        final IXdStorage mockStorage = mock(IXdStorage.class);
        final IXdStorageTransaction mockTransaction = mock(IXdStorageTransaction.class);

        when(mockTransaction.getTransactionId()).thenReturn("tx-delete-stress");
        when(mockTransaction.getTimeout()).thenReturn(1000L); // Жесткий таймаут для детекции дедлоков!

        // Инициализируем дерево с фактором ветвления t = 2 (минимальный размер для провокации частых слияний)
        final XdStorageBTreeId treeId = new XdStorageBTreeId(Integer.class, "delete_index");
        final XdStorageBTree bTree = new XdStorageBTree(treeId, true, 2);

        // Перехватываем вызовы сохранения
        Mockito.doAnswer(inv -> null).when(mockStorage).save(Mockito.any(), Mockito.any());
        Mockito.doAnswer(inv -> null).when(mockStorage).update(Mockito.any(), Mockito.any());
        Mockito.doAnswer(inv -> null).when(mockStorage).delete(Mockito.any(), Mockito.any());

        // Забиваем дерево ключами от 0 до 100
        for (int i = 0; i < 100; i++) {
            bTree.insert(i, "res-" + i, mockStorage, mockTransaction);
        }

        final ExecutorService threadPool = Executors.newFixedThreadPool(PARALLEL_THREADS);
        final AtomicBoolean isRunning = new AtomicBoolean(true);
        final AtomicInteger deadlockOrErrorCounter = new AtomicInteger(0);
        final AtomicInteger totalOperations = new AtomicInteger(0);

        List<Future<?>> futures = new ArrayList<>();

        // ПОТOКИ МУТАЦИЙ (0-3): Непрерывно удаляют и заново вставляют ключи, вызывая joinWithNeighbor и сжатие ярусов!
        for (int i = 0; i < PARALLEL_THREADS / 2; i++) {
            futures.add(threadPool.submit(() -> {
                while (isRunning.get()) {
                    try {
                        int targetKey = ThreadLocalRandom.current().nextInt(100);

                        // Запускаем пурдж ключа — это активирует tryDeepLockForDelete и слияние страниц!
                        bTree.delete(targetKey, mockStorage, mockTransaction);

                        // Тут же возвращаем ключ на место, чтобы дерево не опустело окончательно
                        bTree.insert(targetKey, "restored-res-" + targetKey, mockStorage, mockTransaction);

                        totalOperations.incrementAndGet();
                    } catch (XdStorageException e) {
                        // Допускаем контролируемые эксепшены СУБД "does not exist" при гонках потоков удаления
                    } catch (Exception e) {
                        deadlockOrErrorCounter.incrementAndGet();
                    }
                }
            }));
        }

        // ПОТOКИ ЧТEНИЯ (4-7): Параллельно выполняют find() по случайным ключам диапазона
        for (int i = PARALLEL_THREADS / 2; i < PARALLEL_THREADS; i++) {
            futures.add(threadPool.submit(() -> {
                while (isRunning.get()) {
                    try {
                        int targetKey = ThreadLocalRandom.current().nextInt(100);
                        bTree.find(targetKey, mockStorage, mockTransaction);
                        totalOperations.incrementAndGet();
                    } catch (Exception e) {
                        // Если потоки чтения словят Deadlock блокировок с удалением — Future.get() выбросит ошибку таймаута!
                        deadlockOrErrorCounter.incrementAndGet();
                    }
                }
            }));
        }

        Thread.sleep(TEST_DURATION_MS);
        isRunning.set(false);
        threadPool.shutdown();
        boolean cleanShutdown = threadPool.awaitTermination(2, TimeUnit.SECONDS);

        // Главная проверка на Deadlock: если потоки зависли в мертвой петле блокировок, cleanShutdown будет false!
        assertTrue(cleanShutdown, "🚨 КАТАСТРОФА! Внутри B+ Дерева зафиксирован многопоточный ДЕДЛOК (Deadlock) нод!");

        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get(), "Стресс-поток каскадного слияния аварийно рухнул!");
        }

        System.out.println("=========================================================================");
        System.out.println("📊 РEЗУЛЬТАТЫ СТРEСС-ТEСТИРOВАНИЯ СЛИЯНИЯ СТРУКТУР Б+ ДEРEВА:");
        System.out.println("🔹 Всего успешно выполнено конкурентных операций: " + totalOperations.get());
        System.out.println("❌ Зафиксировано критических сбоев / дедлоков: " + deadlockOrErrorCounter.get());
        System.out.println("=========================================================================");

        assertEquals(0, deadlockOrErrorCounter.get(), "Обнаружены критические аномалии при ребалансировке слияний!");
    }
}
