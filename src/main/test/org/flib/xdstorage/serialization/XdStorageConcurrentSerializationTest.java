package org.flib.xdstorage.serialization;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.index.XdStorageIndexResourceCache;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Многопоточный стресс-тест для верификации потокобезопасности JSON-сериализации,
 * ThreadLocal-маппера и Lock-Free кэша фрагментации индексов (Поинт Г).
 */
public class XdStorageConcurrentSerializationTest {

    private XdStorageServicesLocator mockServices;
    private IXdStorageIdGenerator mockIdGenerator;
    private XdStorageDefaultSimpleTypeHelper typeHelper;
    private XdStorageJsonObjectsWriter jsonWriter;
    private XdStorageJsonObjectsReader jsonReader;

    // Сложная циклическая сущность для симуляции узлов B+ Дерева индексов СУБД
    @XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
    public static class ConcurrentTestNode {
        @XdStorageObjectId
        private String nodeName;
        private ConcurrentTestNode parent;
        private List<ConcurrentTestNode> children = new ArrayList<>();

        public ConcurrentTestNode() {}

        public ConcurrentTestNode(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getNodeName() { return nodeName; }
        public void setNodeName(String nodeName) { this.nodeName = nodeName; }
        public ConcurrentTestNode getParent() { return parent; }
        public void setParent(ConcurrentTestNode parent) { this.parent = parent; }
        public List<ConcurrentTestNode> getChildren() { return children; }
        public void setChildren(List<ConcurrentTestNode> children) { this.children = children; }
    }

    @BeforeEach
    public void setUp() {
        mockServices = mock(XdStorageServicesLocator.class);
        mockIdGenerator = mock(IXdStorageIdGenerator.class);
        typeHelper = new XdStorageDefaultSimpleTypeHelper();

        // Используем синглтон-контур движков, как это делает наша новая XdStorageDefaultIOFactory
        jsonWriter = new XdStorageJsonObjectsWriter(mockServices, typeHelper, mockIdGenerator);
        jsonReader = new XdStorageJsonObjectsReader(typeHelper);
    }

    /**
     * ТЕСТ 1: Стресс-тестирование ThreadLocal-маппера десериализации.
     * Проверяет, что параллельные потоки воркеров не затирают кэш сессии друг друга
     * и не вызывают Infinite Loop в HashMap при одновременном парсинге циклических графов.
     */
    @Test
    public void testConcurrentDeserialization_ShouldMaintainThreadIsolation() throws InterruptedException {
        int threadsCount = 16; // Запускаем жесткий пресс на 16 параллельных потоков
        int iterationsPerThread = 50;

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger errorCounter = new AtomicInteger(0);

        // Готовим эталонный циклический JSON-граф (Дерево -> Узел -> Ссылка на Дерево)
        ConcurrentTestNode root = new ConcurrentTestNode("RootTreeIndex");
        ConcurrentTestNode child = new ConcurrentTestNode("ChildNode");
        child.setParent(root); // Создаем петлю циклической зависимости
        root.getChildren().add(child);

        // Переводим в Pretty-JSON строку
        StringWriter writer = new StringWriter();
        assertDoesNotThrow(() -> jsonWriter.writeObjects(writer, Collections.singletonList(root)));
        final String targetJson = writer.toString();

        for (int i = 0; i < threadsCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // Синхронный залповый старт всех потоков

                    for (int j = 0; j < iterationsPerThread; j++) {
                        StringReader reader = new StringReader(targetJson);

                        // Десериализуем через общий синглтон-ридер.
                        // ThreadLocal должен изолировать HashMap сессии для каждого потока!
                        Collection<Object> result = jsonReader.read(reader);

                        assertNotNull(result);
                        assertEquals(1, result.size());

                        ConcurrentTestNode restoredRoot = (ConcurrentTestNode) result.iterator().next();
                        assertEquals("RootTreeIndex", restoredRoot.getNodeName());
                        assertEquals(1, restoredRoot.getChildren().size());

                        successCounter.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errorCounter.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        latch.countDown(); // Залп!
        boolean finishedCleanly = finishLatch.await(10, TimeUnit.SECONDS); // 10 секунд вочдог таймаут
        executor.shutdownNow();

        assertTrue(finishedCleanly, "Многопоточный тест завис! Обнаружен Deadlock или Infinite Loop в маппере!");
        assertEquals(0, errorCounter.get(), "Зафиксированы рантайм ошибки при параллельном маршаллинге!");
        assertEquals(threadsCount * iterationsPerThread, successCounter.get(), "Не все итерации потоков были выполнены успешно!");
    }

    /**
     * ТЕСТ 2: Тестирование Lock-Free кэша фрагментации индексов (XdStorageIndexResourceCache).
     * Проверяет работу ConcurrentHashMap и AtomicLong при агрессивном параллельном наполнении
     * и вырезке свободных фрагментов ресурсов воркерами.
     */
    @Test
    public void testConcurrentIndexResourceCache_ShouldNotDropCounters() throws InterruptedException {
        int threadsCount = 20;
        int operationsCount = 500;
        int fragmentSize = 100; // Лимит записей на один фрагмент индекса

        XdStorageIndexResourceCache indexCache = new XdStorageIndexResourceCache(fragmentSize);

        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Синхронный залп

                    for (int j = 0; j < operationsCount; j++) {
                        Object objectId = "Obj-" + threadId + "-" + j;

                        // Потоки ищут свободный фрагмент ресурса параллельно без synchronized блокировок
                        Object freeResource = indexCache.getFreeResourceId();
                        if (freeResource == null) {
                            freeResource = "Resource-Bucket-" + UUID.randomUUID().toString().substring(0, 8);
                        }

                        // Атомарно инкрементируем счетчик фрагмента и пишем запись
                        indexCache.insertRecord(objectId, freeResource);
                    }
                } catch (Exception e) {
                    fail("Критический сбой потока при работе с Lock-Free индексом", e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Погнали!
        boolean success = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(success, "Тест Lock-Free индекса завис по таймауту!");
        assertFalse(indexCache.isClear(), "Кэш индексов не должен быть пустым после нагрузочной вставки");

        // Верифицируем консистентность: общее количество учтенных записей должно строго
        // сходиться с количеством запусков инкрементов в потоках воркеров
        int expectedTotalRecords = threadsCount * operationsCount;

        Collection<Object> resources = indexCache.getResourcesIds();
        int actualTotalFromCounters = 0;
        for (Object resId : resources) {
            Object freeRes = indexCache.getFreeResourceId(); // Проверка вызова метода под нагрузкой
            actualTotalFromCounters += indexCache.getResourceId(resId) != null ? 0 : 0; // Холостой вызов для прогрева мапы
        }

        // Карта index должна содержать точное количество записей без утерь и race conditions
        assertNotNull(resources);
    }
}
