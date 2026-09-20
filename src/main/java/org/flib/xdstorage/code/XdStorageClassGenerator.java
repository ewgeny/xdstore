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

    public static Map<Class<?>, Class<?>> generateUnmodifiableWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();
        if (cl == XdStorageIdentifiableObject.class) {
            new XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        } else {
            new XdStorageUnmodifiableWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);
        }
        return compileGeneratedClassesCode(cl, generatedCode);
    }

    public static Map<Class<?>, Class<?>> generateSimpleWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();

        new XdStorageSimpleWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);

        return compileGeneratedClassesCode(cl, generatedCode);
    }

    public static Map<Class<?>, Class<?>> generateObservableWrapper(final Class<?> cl) throws IOException {
        final Map<String, String> generatedCode = new HashMap<>();

        new XdStorageObservableWrapperClassCodeGenerator().generate(CLASSES_PACKAGE, cl, generatedCode);

        return compileGeneratedClassesCode(cl, generatedCode);
    }

    private static Map<Class<?>, Class<?>> compileGeneratedClassesCode(final Class<?> cl, final Map<String, String> generatedCode) throws IOException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

        final XdStorageJavaSourceFromString[] files = new XdStorageJavaSourceFromString[generatedCode.size()];
        int i = 0;
        for (final Map.Entry<String, String> entry : generatedCode.entrySet()) {
            files[i++] = new XdStorageJavaSourceFromString(entry.getKey(), entry.getValue());
        }

        final Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(files);
        final File path = new File(CODE_DIRECTORY, CLASSES_PACKAGE.replace('.', '/'));
        if (!path.exists())
            path.mkdirs();
        final Iterable<String> options = Arrays.asList(new String[]{"-d", CODE_DIRECTORY});
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null, compilationUnits);

        boolean success = task.call();
        for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
//            System.out.println(diagnostic.getCode());
//            System.out.println(diagnostic.getKind());
//            System.out.println(diagnostic.getPosition());
//            System.out.println(diagnostic.getStartPosition());
//            System.out.println(diagnostic.getEndPosition());
//            System.out.println(diagnostic.getSource());
            log.warn(diagnostic.getMessage(null));
        }
        if (success) {
            for (final XdStorageJavaSourceFromString source : files) {
                log.info("Generation class '" + source.getName() + "' success: " + success);
            }

            if (success) {
                try {
                    final URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{new File(CODE_DIRECTORY).toURI().toURL()});

                    final Map<Class<?>, Class<?>> result = new HashMap<>();
                    for (final Map.Entry<String, String> entry : generatedCode.entrySet()) {
                        Class<?> clazz = Class.forName(entry.getKey(), true, classLoader);
                        result.put(cl, clazz);
                    }
                    return result;
                } catch (ClassNotFoundException e) {
                    log.error("cannot load generated class", e);
                }
            }
        } else {
            for (final XdStorageJavaSourceFromString source : files) {
                log.info("Generation class '" + source.getName() + "' success: " + success);
            }
        }

        return new HashMap<>();
    }
}

