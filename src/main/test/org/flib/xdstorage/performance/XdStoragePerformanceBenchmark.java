package org.flib.xdstorage.performance;

import org.flib.xdstorage.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput) // Измеряем количество операций в секунду
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(2) // Делаем 2 независимых перезапуска JVM для исключения случайных аномалий ОС
@Warmup(iterations = 4, time = 2) // Увеличиваем прогрев до 4 итераций по 2 секунды
@Measurement(iterations = 5, time = 2) // Делаем 5 чистых измерительных прогонов по 2 секунды
@Threads(4) // РАЗГОН: Запускаем бенчмарк параллельно в 4 потока!
public class XdStoragePerformanceBenchmark {

    private IXdFileStorage storage;
    private static final String TEST_DIR = "./target/jmh_bench_storage";

    @Setup(Level.Trial)
    public void setUp() {
        // Очищаем старую дисковую партицию перед запуском
        deleteDir(new File(TEST_DIR));
        // Инициализируем хранилище с размером фрагмента кэша 1024 элемента
        storage = XdStorageProvider.newOrGetFileStorage("BenchStorage", TEST_DIR, 1024);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (storage != null) {
            storage.shutdown();
        }
        deleteDir(new File(TEST_DIR));
    }

    @Benchmark
    public void benchmarkTransactionWriteAndReadFlow() throws Exception {
        // Генерируем уникальный ID для каждой операции, чтобы потоки не конфликтовали по ключам
        String uniqueId = UUID.randomUUID().toString();

        // Используем BenchmarkEntity, которая благодаря твоей вчерашней правке
        // имеет аннотацию @XdStorageObjectPolicy!
        BenchmarkEntity entity = new BenchmarkEntity(uniqueId, "JMH_MultiThread_Payload");

        // Фиксируем чистый сквозной транзакционный цикл
        IXdStorageTransaction tx = storage.beginTransaction(3000L); // таймаут 3 секунды
        try {
            storage.save(entity, tx);
            storage.load(BenchmarkEntity.class, uniqueId, tx);
            storage.commitTransaction(tx);
        } catch (Exception e) {
            storage.rollbackTransaction(tx);
            throw e;
        }
    }

    private void deleteDir(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDir(f);
            }
        }
        file.delete();
    }

    public static void main(String[] args) throws Exception {
        // Точка входа для запуска бенчмарка прямо из IDE или через JAR
        Options opt = new OptionsBuilder()
                .include(XdStoragePerformanceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
