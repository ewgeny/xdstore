package org.flib.xdstorage.code;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.code.generation.*;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Исправленная декомпозированная реализация генератора байт-кода Simple-оберток.
 * Конфликт прав доступа Java решен через изоляцию моста-утилиты utilsBridge.
 */
public class XdStorageSimpleWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    private final List<XdStorageMethodGroupGenerator> generators = new ArrayList<>();

    public XdStorageSimpleWrapperClassCodeGenerator() {
        super(true); // Активируем логику генерации ленивой загрузки по get
        // Регистрация изолированных модулей генерации
        generators.add(new SimpleGettersGenerator());
        generators.add(new StrongGettersGenerator());
        generators.add(new LazyLoadGettersGenerator());
        generators.add(new SettersMethodsGenerator());
        generators.add(new ClosedMethodsGenerator());
        generators.add(new StandardObjectMethodsGenerator());
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // ИСПРАВЛЕНИЕ RACE CONDITION: Создаем изолированный контекст для текущего потока
        final GenerationContext ctx = new GenerationContext();
        collectMethodsLocal(cl, ctx);

        final StringBuilder builder = new StringBuilder();
        builder.append("package ").append(classesPackage).append(";\r\n\r\n");

        final String className = buildClassName(classesPackage, cl);
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageSimpleWrapper.class.getName()).append(" {\r\n");

        // Сборка каркаса полей и конструктора
        buildFieldsAndConstructor(builder, cl, className);
        buildServiceWrapperMethods(builder, cl);

        // ИСПРАВЛЕНИЕ: Создаем мост-адаптер на месте, делегируя вызовы protected-методам родителя
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

        // Последовательно передаем сборку методов зарегистрированным стратегиям через безопасный мост
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
        builder.append("\r\n\tprivate volatile boolean reference__ = true;\r\n");
        builder.append("\r\n\tprivate ").append(Lock.class.getName()).append(" lock__ = new ").append(ReentrantLock.class.getName()).append("();\r\n");

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
    }

    private void buildServiceWrapperMethods(StringBuilder builder, Class<?> cl) {
        final String idFieldName = getIdFieldName(cl);
        final String idGetterName = "get" + Character.toUpperCase(idFieldName.charAt(0)) + idFieldName.substring(1);

        builder.append("\r\n\t@Override\r\n\tpublic Object getObjectId__() { return object.").append(idGetterName).append("(); }\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic void setReference__(boolean r) { this.reference__ = r; }\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic boolean isReference__() { return this.reference__; }\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic void lock__() { lock__.lock(); }\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic void unlock__() { lock__.unlock(); }\r\n");
        builder.append("\r\n\tpublic static boolean isDummySimpleWrapper() { return false; }\r\n");
    }

    @Override
    protected String buildClassName(String classesPackage, Class<?> cl) {
        return cl.getSimpleName() + "SimpleWrapper";
    }
}
