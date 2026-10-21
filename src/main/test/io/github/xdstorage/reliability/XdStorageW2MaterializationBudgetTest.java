package io.github.xdstorage.reliability;

import io.github.xdstorage.BenchmarkEntity;
import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.XdStorageConfig;
import io.github.xdstorage.XdStorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W-2: per-transaction materialization budget (txMaterializedObjectsCap, default 0 =
 * unlimited). Превышение — громкий отказ с подсказкой вместо молчаливого роста кучи.
 * WRITE_OUT (коммитная выписка) сознательно не бюджетируется: сохранение больших
 * объемов при маленьком бюджете продолжает работать.
 */
@DisplayName("W-2: бюджет материализации транзакции")
public class XdStorageW2MaterializationBudgetTest {

    private static final String NAME = "w2-budget-db";
    private static final int DATASET = 20;

    private XdStorageConfig capped(final long cap) {
        return XdStorageConfig.builder().txMaterializedObjectsCap(cap).build();
    }

    @Test
    @DisplayName("Скан сверх бюджета — громкий отказ с подсказкой; в пределах бюджета — работает")
    public void testScanBudgetExceededFailsLoudly() throws Exception {
        final Path dir = Files.createTempDirectory("w2_scan");
        try {
            saveDataset(dir);

            // скан материализует ВЕСЬ класс: в пределах бюджета — читается целиком
            final IXdFileStorage within = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128, capped(25));
            try {
                final IXdStorageTransaction tx = within.beginTransaction();
                assertEquals(DATASET, within.load(BenchmarkEntity.class, tx).size(),
                        "scan within the budget must succeed");
                tx.commit();
            } finally {
                within.shutdown();
            }

            // полная транзакция сверх бюджета — громкий отказ
            final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128, capped(5));
            try {
                final IXdStorageTransaction bigTx = storage.beginTransaction();
                assertThrows(Exception.class, () -> storage.load(BenchmarkEntity.class, bigTx),
                        "scan over the budget must fail loudly instead of growing the heap");
                bigTx.rollback();
            } finally {
                storage.shutdown();
            }
        } finally {
            deleteQuietly(dir);
        }
    }

    @Test
    @DisplayName("По-ID чтения сверх бюджета (бюджет копится по транзакции) — громкий отказ")
    public void testByIdBudgetAccumulates() throws Exception {
        final Path dir = Files.createTempDirectory("w2_byid");
        try {
            saveDataset(dir);

            final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128, capped(3));
            try {
                final IXdStorageTransaction tx = storage.beginTransaction();
                assertNotNull(storage.load(BenchmarkEntity.class, "b-1", tx));
                assertNotNull(storage.load(BenchmarkEntity.class, "b-2", tx));
                assertNotNull(storage.load(BenchmarkEntity.class, "b-3", tx));
                assertThrows(Exception.class, () -> storage.load(BenchmarkEntity.class, "b-4", tx),
                        "the read crossing the budget must fail loudly");
                tx.rollback();
            } finally {
                storage.shutdown();
            }
        } finally {
            deleteQuietly(dir);
        }
    }

    @Test
    @DisplayName("WRITE_OUT не бюджетируется: сохранение 30 объектов при cap=5 работает")
    public void testWriteOutExemptFromBudget() throws Exception {
        final Path dir = Files.createTempDirectory("w2_writeout");
        try {
            final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128, capped(5));
            try {
                final IXdStorageTransaction tx = storage.beginTransaction();
                for (int i = 0; i < 30; i++) {
                    storage.save(new BenchmarkEntity("w-" + i, "payload-" + i), tx);
                }
                tx.commit();
            } finally {
                storage.shutdown();
            }

            // верификация чтением — с конфигом по умолчанию (чтение 30 объектов бюджетом cap=5
            // честно запрещено: бюджет считает именно ЧТЕНИЯ)
            final IXdFileStorage reader = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128);
            try {
                final IXdStorageTransaction tx = reader.beginTransaction();
                assertEquals(30, reader.load(BenchmarkEntity.class, tx).size());
                tx.commit();
            } finally {
                reader.shutdown();
            }
        } finally {
            deleteQuietly(dir);
        }
    }

    @Test
    @DisplayName("Умолчание (0): бюджет не ограничивает ничего")
    public void testDefaultUnlimited() throws Exception {
        final Path dir = Files.createTempDirectory("w2_default");
        try {
            saveDataset(dir);
            final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128);
            try {
                final IXdStorageTransaction tx = storage.beginTransaction();
                assertEquals(DATASET, storage.load(BenchmarkEntity.class, tx).size());
                tx.commit();
            } finally {
                storage.shutdown();
            }
        } finally {
            deleteQuietly(dir);
        }
    }

    private void saveDataset(final Path dir) throws Exception {
        final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(NAME, dir.toString(), 128);
        try {
            final IXdStorageTransaction tx = storage.beginTransaction();
            for (int i = 1; i <= DATASET; i++) {
                storage.save(new BenchmarkEntity("b-" + i, "payload-" + i), tx);
            }
            tx.commit();
        } finally {
            storage.shutdown();
        }
    }

    private void deleteQuietly(final Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (final Throwable ignored) {
        }
    }
}
