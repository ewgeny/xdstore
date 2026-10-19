package org.flib.xdstorage.reliability;

import org.flib.xdstorage.BenchmarkEntity;
import org.flib.xdstorage.IXdFileStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.metrics.XdStorageMetrics;
import org.flib.xdstorage.XdStorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O-1: счетчики операций доступны через публичный API хранилища и отражают жизненный цикл.
 */
@DisplayName("O-1: метрики операций хранилища")
public class XdStorageMetricsTest {

    @Test
    @DisplayName("Транзакционные и write-out счетчики заполняются реальной работой и видны через getMetrics()")
    public void testCountersReflectStorageWork() throws Exception {
        final Path dir = Files.createTempDirectory("o1_metrics_db");
        try {
            // классический путь записи (журнал выключен): счетчик RESOURCE_WRITE_OUTS
            // считается на полной перезаписи; журнализированные коммиты идут аппендами
            final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage("o1-metrics", dir.toString(), 128,
                    org.flib.xdstorage.XdStorageConfig.builder().journalWrites(false).build());
            try {
                final IXdStorageTransaction tx = storage.beginTransaction();
                storage.save(new BenchmarkEntity("m-1", "alpha"), tx);
                storage.save(new BenchmarkEntity("m-2", "beta"), tx);
                tx.commit();

                final IXdStorageTransaction readTx = storage.beginTransaction();
                storage.load(BenchmarkEntity.class, readTx);
                readTx.commit();

                final XdStorageMetrics metrics = storage.getMetrics();
                assertTrue(metrics.get(XdStorageMetrics.TRANSACTIONS_BEGUN) >= 2,
                        "at least the two begun transactions counted: " + metrics);
                assertTrue(metrics.get(XdStorageMetrics.TRANSACTIONS_COMMITTED) >= 2,
                        "at least the two commits counted: " + metrics);
                assertTrue(metrics.get(XdStorageMetrics.RESOURCE_WRITE_OUTS) >= 1,
                        "commit must produce a resource write-out: " + metrics);
                assertTrue(metrics.get(XdStorageMetrics.RESOURCE_WRITE_OUT_NANOS) >= 0);
                assertEquals(0L, metrics.get(XdStorageMetrics.ACTIVE_TRANSACTIONS),
                        "committed transactions must not stay active: " + metrics);

                final Map<String, Long> snapshot = metrics.snapshot();
                assertFalse(snapshot.isEmpty());
                assertTrue(metrics.toString().contains("transactionsBegun"));
            } finally {
                storage.shutdown();
            }

            // JVM-wide engine-anomaly sink is accessible and starts empty-ish (monotonic advisory)
            assertNotNull(XdStorageMetrics.engineEvents().snapshot());
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (final Throwable ignored) {
            }
        }
    }
}
