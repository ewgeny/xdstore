package io.github.xdstorage.code;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import io.github.xdstorage.object.XdStorageIdentifiableObject;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class XdStorageClassGenerator {

    private static final Logger log = LoggerFactory.getLogger(XdStorageClassGenerator.class);
    private static final String CODE_DIRECTORY = "gencode";
    private static final String CLASSES_PACKAGE = "io.github.xdstorage.code";

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
        if (compiler == null) {
            // SILENT-LOSS BARRIER: без компилятора (JRE) сгенерированные обертки не создаются,
            // движок уходит в dummy-обертки с поврежденными equals/hashCode — молчать нельзя.
            log.error("No system Java compiler available (running on a JRE?) — wrapper generation for {} "
                    + "silently degrades to dummy wrappers (broken equals/hashCode contracts!)", cl.getName());
            return new HashMap<>();
        }

        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        final XdStorageJavaSourceFromString[] files = new XdStorageJavaSourceFromString[generatedCode.size()];
        int i = 0;
        for (final Map.Entry<String, String> entry : generatedCode.entrySet()) {
            files[i++] = new XdStorageJavaSourceFromString(entry.getKey(), entry.getValue());
        }

        final Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(files);
        // R-48: под манифест-бутером surefire System.getProperty("java.class.path") содержит
        // только booter.jar - рантайм-компилятор оберток не видел классы сущностей и молча
        // деградировал в dummy-обертки (а чтение сущностей падало в CHM.put(null)).
        // Расширяем classpath кодовыми источниками генерируемого графа: сама сущность,
        // ее суперклассы и ядро СУБД. В обычном запуске это no-op (они уже в java.class.path).
        String currentClasspath = buildCompilationClasspath(cl);
        final Iterable<String> options = Arrays.asList("-d", CODE_DIRECTORY, "-classpath", currentClasspath);
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null, compilationUnits);

        boolean success = task.call();
        if (!success) {
            // SILENT-LOSS BARRIER: ошибка компиляции обертки раньше проглатывалась — движок
            // уходил в dummy-обертки без единого следа в логе. Печатаем диагностику компилятора.
            log.error("Wrapper compilation FAILED for {} ({} files): {}", cl.getName(), generatedCode.size(),
                    diagnostics.getDiagnostics());
            if (Boolean.getBoolean("xdstorage.dump.gencode")) {
                generatedCode.forEach((name, source) -> {
                    try {
                        java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/gencode-dump-" + name.replaceAll("[^A-Za-z0-9.$]", "_") + ".java"),
                                source.getBytes());
                    } catch (final Exception dumpError) {
                        log.error("gencode dump failed for {}", name, dumpError);
                    }
                });
            }
        }
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

    private static String buildCompilationClasspath(final Class<?> cl) {
        final java.util.Set<String> entries = new java.util.LinkedHashSet<>();
        entries.addAll(Arrays.asList(System.getProperty("java.class.path", "").split(File.pathSeparator)));
        entries.removeIf(String::isEmpty);

        // кодовые источники иерархии сущности (обычно target/test-classes или jar сущности)
        for (Class<?> c = cl; c != null && c != Object.class; c = c.getSuperclass()) {
            addCodeSource(entries, c);
        }
        // кодовый источник ядра СУБД (все движковые типы, на которые ссылается обертка)
        addCodeSource(entries, XdStorageClassGenerator.class);
        addCodeSource(entries, io.github.xdstorage.observing.XdStorageAbstractIdObserver.class);

        return String.join(File.pathSeparator, entries);
    }

    private static void addCodeSource(final java.util.Set<String> entries, final Class<?> c) {
        try {
            final java.security.CodeSource source = c.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                entries.add(new File(source.getLocation().toURI()).getAbsolutePath());
            }
        } catch (final Exception e) {
            log.debug("cannot resolve code source of {}", c.getName(), e);
        }
    }
}
