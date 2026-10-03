package org.flib.xdstorage.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class XdStorageClassGenerator {

    private static final Logger log = LogManager.getLogger(XdStorageClassGenerator.class);
    private static final String CODE_DIRECTORY = "gencode";
    private static final String CLASSES_PACKAGE = "org.flib.xdstorage.code";

    // АРХИТЕКТУРНОЕ ИСПРАВЛЕНИЕ: Выносим URLClassLoader в единое статическое переиспользуемое поле СУБД!
    // Это полностью пресекает утечки памяти в Metaspace JVM и ликвидирует взаимные дедлоки
    // параллельных потоков при иерархическом вызове Class.forName()!
    private static final URLClassLoader sharedClassLoader;

    static {
        try {
            final File path = new File(CODE_DIRECTORY, CLASSES_PACKAGE.replace('.', '/'));
            if (!path.exists()) {
                path.mkdirs();
            }
            sharedClassLoader = URLClassLoader.newInstance(
                    new URL[]{new File(CODE_DIRECTORY).toURI().toURL()},
                    XdStorageClassGenerator.class.getClassLoader()
            );
        } catch (Exception e) {
            throw new RuntimeException("Критическая ошибка инициализации транзакционного ClassLoader СУБД", e);
        }
    }

    public static synchronized Map<Class<?>, Class<?>> generateUnmodifiableWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();
        if (cl == XdStorageIdentifiableObject.class) {
            new XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        } else {
            new XdStorageUnmodifiableWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        }
        return compileGeneratedClassesCode(cl, generatedCode);
    }

    public static synchronized Map<Class<?>, Class<?>> generateSimpleWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();
        new XdStorageSimpleWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        return compileGeneratedClassesCode(cl, generatedCode);
    }

    public static synchronized Map<Class<?>, Class<?>> generateObservableWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();
        new XdStorageObservableWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        return compileGeneratedClassesCode(cl, generatedCode);
    }

    private static Map<Class<?>, Class<?>> compileGeneratedClassesCode(final Class<?> cl, final Map<String, String> generatedCode) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) return new HashMap<>();

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final XdStorageJavaSourceFromString[] files = new XdStorageJavaSourceFromString[generatedCode.size()];
        int i = 0;
        for (final Map.Entry<String, String> entry : generatedCode.entrySet()) {
            files[i++] = new XdStorageJavaSourceFromString(entry.getKey(), entry.getValue());
        }

        final Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(files);
        String currentClasspath = System.getProperty("java.class.path");
        final Iterable<String> options = Arrays.asList("-d", CODE_DIRECTORY, "-classpath", currentClasspath);
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null, compilationUnits);

        boolean success = task.call();
        if (success) {
            try {
                final Map<Class<?>, Class<?>> result = new HashMap<>();
                for (final Map.Entry<String, String> entry : generatedCode.entrySet()) {
                    // Используем наш единый разделяемый sharedClassLoader СУБД
                    Class<?> clazz = Class.forName(entry.getKey(), true, sharedClassLoader);
                    result.put(cl, clazz);
                }
                return result;
            } catch (ClassNotFoundException e) {
                log.error("cannot load generated class", e);
            }
        }
        return new HashMap<>();
    }
}
