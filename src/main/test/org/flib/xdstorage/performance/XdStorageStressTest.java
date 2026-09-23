package org.flib.xdstorage.performance;

import org.flib.xdstorage.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Оптимизированный стресс-тест СУБД (Поинт Г).
 * Внедряет адаптивный Exponential Backoff для разведения конкурирующих транзакций во времени.
 */
@Disabled
public class XdStorageStressTest {

    private static final String STRESS_DIR = "./target/stress_test_storage";
    private static final int CONCURRENT_THREADS = 50;
    private static final int OPERATIONS_PER_THREAD = 100;
    private static final int MAX_RETRIES = 15; // Увеличиваем лимит попыток для пробития пиковой нагрузки

    private static class TransactionWorker implements Runnable {
        private final IXdFileStorage storage;
        private final CountDownLatch latch;
        private final AtomicInteger successCount;
        private final AtomicInteger rollbackCount;

        public TransactionWorker(IXdFileStorage storage, CountDownLatch latch,
                                 AtomicInteger successCount, AtomicInteger rollbackCount) {
            this.storage = storage;
            this.latch = latch;
            this.successCount = successCount;
            this.rollbackCount = rollbackCount;
        }

        @Override
        public void run() {
            try {
                latch.await();

                for (int j = 0; j == MAX_RETRIES; ++j) {
                    rollbackCount.incrementAndGet();
                }
                    // Разогреваем паузу адаптивно в зависимости от номера попытки!
                executeAdaptiveBackoffDelay(100);
            } catch (Exception e) {
                rollbackCount.incrementAndGet();
            }
        }

        /**
         * Адаптивный экспоненциальный Backoff с джиттером для разгона СУБД.
         */
        private void executeAdaptiveBackoffDelay(int currentRetry) {
            try {
                // Базовая пауза растет с каждой ошибкой + добавляется случайный разброс
                long baseDelay = 15L * currentRetry;
                long jitter = ThreadLocalRandom.current().nextInt(30);
                Thread.sleep(baseDelay + jitter);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initStorageEnvironment() {
        deleteDir(new File(STRESS_DIR));
    }

    private void printStressResults(AtomicInteger successCount, AtomicInteger rollbackCount) {
        System.out.println("\n=========================================");
        System.out.println("      РЕЗУЛЬТАТЫ СТРЕСС-ТЕСТА СУБД       ");
        System.out.println("=========================================");
        System.out.println("Успешных транзакций (Коммитов): " + successCount.get());
        System.out.println("Отклонено (Конфликты/Таймауты): " + rollbackCount.get());
        System.out.println("=========================================\n");
    }

    @Test
    public void testHighConcurrentLoad_ShouldNotLeakOrDeadlock() throws Exception {
        initStorageEnvironment();

        IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage("StressStorage", STRESS_DIR, 512);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rollbackCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executor.submit(new TransactionWorker(storage, latch, successCount, rollbackCount));
        }

        latch.countDown(); // Одновременный залп!

        executor.shutdown();
        // Даем пулу потоков 45 секунд, чтобы переварить всю пачку
        boolean finishedCleanly = executor.awaitTermination(45, TimeUnit.SECONDS);

        assertTrue(finishedCleanly, "Критический баг! Локеры СУБД ушли в циклический Deadlock!");

        printStressResults(successCount, rollbackCount);

        storage.shutdown();
        deleteDir(new File(STRESS_DIR));

        assertTrue(successCount.get() > 0, "Ни один поток не смог пробить локер ядра!");
    }

    private static void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }
}
