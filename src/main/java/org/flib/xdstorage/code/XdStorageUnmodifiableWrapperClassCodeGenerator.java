package org.flib.xdstorage.code;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.generation.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Исправленная декомпозированная реализация генератора байт-кода Unmodifiable-оберток.
 * Ошибка прав доступа решена путем отделения утилитного моста от наследования базового класса.
 */
public class XdStorageUnmodifiableWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    private final List<XdStorageUnmodifiableMethodGenerator> generators = new ArrayList<>();

    public XdStorageUnmodifiableWrapperClassCodeGenerator() {
        super(true); // Активируем логику интроспекции методов ленивой загрузки

        // Регистрация изолированных модулей генерации неизменяемых прокси
        generators.add(new SimpleGettersGeneratorAdapter());
        generators.add(new UnmodifiableStrongGettersGenerator());
        generators.add(new UnmodifiableLazyLoadGettersGenerator());
        generators.add(new UnmodifiableBlockMutationsGenerator());
        generators.add(new StandardObjectMethodsGeneratorAdapter());
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // Локальный изолированный контекст для текущего потока компиляции
        final GenerationContext ctx = new GenerationContext();
        collectMethodsLocal(cl, ctx);

        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(classesPackage).append(";\r\n\r\n");

        final String className = buildClassName(classesPackage, cl);
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageUnmodifiableWrapper.class.getName()).append(" {\r\n");

        // Поля каркаса прокси-класса и конструктор
        buildFieldsAndConstructor(builder, cl, className);

        // ИСПРАВЛЕНИЕ: Создаем мост-адаптер на месте, делегируя вызовы public/protected методам родителя
        XdStorageMethodBuilderUtils utilsBridge = new XdStorageMethodBuilderUtils() {
            @Override
            public String callBuildMethodDefinition(int mod, String name, java.lang.reflect.TypeVariable<Method>[] tp, Class<?> ret, java.lang.reflect.Type gRet, Class<?>[] p, java.lang.reflect.Type[] gP, Class<?>[] ex) {
                return buildMethodDefinition(mod, name, tp, ret, gRet, p, gP, ex);
            }

            @Override
            public String callBuildMethodCalling(String name, Class<?>[] parameterTypes) {
                return buildMethodCalling(name, parameterTypes);
            }
        };

        // Последовательно передаем сборку методов стратегиям через безопасный мост
        for (int i = 0; i < generators.size(); i++) {
            generators.get(i).generate(builder, cl, ctx, utilsBridge);
        }

        builder.append("}\r\n");
        code.put(classesPackage + "." + className, builder.toString());
        return code;
    }

    private void buildFieldsAndConstructor(StringBuilder builder, Class<?> cl, String className) {
        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate ").append(Object.class.getName()).append(" parent;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorage.class.getName()).append(" storage;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorageTransaction.class.getName()).append(" transaction;\r\n");

        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(Object.class.getName()).append(" parent, ");
        builder.append(cl.getName()).append(" object, ");
        builder.append(IXdStorage.class.getName()).append(" storage, ");
        builder.append(IXdStorageTransaction.class.getName()).append(" transaction) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t\tthis.parent = parent;\r\n");
        builder.append("\t\tthis.storage = storage;\r\n");
        builder.append("\t\tthis.transaction = transaction;\r\n");
        builder.append("\t}\r\n");
        builder.append("\r\n\tpublic static boolean isDummyUnmodifiableWrapper() { return false; }\r\n");
    }

    @Override
    protected String buildClassName(String classesPackage, Class<?> cl) {
        return cl.getSimpleName() + "UnmodifiableWrapper";
    }
}
