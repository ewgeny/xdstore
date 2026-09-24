package org.flib.xdstorage.code;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Исправленный комплект JUnit 5 тестов для проверки динамической кодогенерации СУБД.
 * Избавлен от статических вложенных классов для предотвращения синтаксических сбоев JavaCompiler.
 */
public class XdStorageClassGeneratorTest {

    private static class ConcreteTestCodeGenerator extends XdStorageAbstractClassCodeGenerator {
        @Override
        public Map<String, String> generate(String classesPackage, Class<?> cl, Map<String, String> code) {
            collectMethodsAndClasses(cl);
            return code;
        }
        @Override
        protected String buildClassName(String classesPackage, Class<?> cl) {
            return cl.getSimpleName() + "TestWrapper";
        }
    }

    @Test
    public void testBuildMethodCalling_ShouldFormatCorrectly() {
        ConcreteTestCodeGenerator generator = new ConcreteTestCodeGenerator();
        Class<?>[] params = new Class<?>[]{String.class, int.class};
        String callingStr = generator.buildMethodCalling("saveData", params);
        assertEquals("saveData(var0, var1)", callingStr);
    }

    @Test
    public void testGetIdFieldName_ShouldDetectAnnotatedField() {
        ConcreteTestCodeGenerator generator = new ConcreteTestCodeGenerator();
        String idFieldName = generator.getIdFieldName(TestGeneratedEntity.class);
        assertEquals("codeId", idFieldName);
    }

    @Test
    public void testCollectMethods_ShouldParseGettersAndSetters() {
        ConcreteTestCodeGenerator generator = new ConcreteTestCodeGenerator();
        Map<String, String> dummyCode = new HashMap<>();
        generator.generate("org.flib.xdstorage.code", TestGeneratedEntity.class, dummyCode);

        assertFalse(generator.fieldsGetters.isEmpty(), "Должен найти простой геттер");
        assertFalse(generator.setters.isEmpty(), "Должен найти сеттеры");
    }

    @Test
    public void testGenerateSimpleWrapper_ShouldCompileSuccessfullyOnTheFly() {
        assertDoesNotThrow(() -> {
            Map<Class<?>, Class<?>> wrappers = XdStorageClassGenerator.generateSimpleWrapper(ComplexEntityForGeneration.class);
            assertNotNull(wrappers);
            assertFalse(wrappers.isEmpty(), "Карта сгенерированных прокси-классов не должна быть пустой!");

            Class<?> proxyClass = wrappers.get(ComplexEntityForGeneration.class);
            assertNotNull(proxyClass);
            assertTrue(ComplexEntityForGeneration.class.isAssignableFrom(proxyClass));
            assertTrue(IXdStorageSimpleWrapper.class.isAssignableFrom(proxyClass));
        });
    }

    @Test
    public void testGenerateUnmodifiableWrapper_ShouldCompileSuccessfully() {
        assertDoesNotThrow(() -> {
            Map<Class<?>, Class<?>> wrappers = XdStorageClassGenerator.generateUnmodifiableWrapper(ComplexEntityForGeneration.class);
            assertNotNull(wrappers);
            assertFalse(wrappers.isEmpty());

            Class<?> unmodifiableProxy = wrappers.get(ComplexEntityForGeneration.class);
            assertNotNull(unmodifiableProxy);
            assertTrue(IXdStorageUnmodifiableWrapper.class.isAssignableFrom(unmodifiableProxy));
        });
    }

    @Test
    public void testConcurrentGeneration_ShouldNotThrowConcurrentModificationException() throws Exception {
        int threadCount = 4;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    XdStorageClassGenerator.generateSimpleWrapper(ComplexEntityForGeneration.class);
                } catch (Throwable t) {
                    hasError.set(true);
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        boolean finishedCleanly = executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertTrue(finishedCleanly);
        assertFalse(hasError.get(), "Критическая ошибка конкурентности в ArrayList генератора!");
    }
}
