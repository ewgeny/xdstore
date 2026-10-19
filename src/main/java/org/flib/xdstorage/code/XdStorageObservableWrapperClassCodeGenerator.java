package org.flib.xdstorage.code;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;

import java.lang.reflect.*;
import java.util.Map;

/**
 * Декомпозированный stateless генератор реактивных Observable оберток (Поинт В / Г).
 * Полностью избавлен от разделяемых полей и изолирован на уровне локального стек-контекста.
 */
public class XdStorageObservableWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // 1. Собираем методы во внутренний изолированный GenerationContext нити
        final GenerationContext ctx = collectMethodsAndClasses(cl);

        final StringBuilder builder = new StringBuilder();
        final String className = buildClassName(classesPackage, cl);

        // 2. Декларативный конвейер сборки структуры реактивного класса
        appendPackageAndClassHeader(builder, classesPackage, className, cl);
        appendPrivateFields(builder, cl);
        appendConstructor(builder, className, cl);
        appendObserverRegistryMethods(builder);

        // 3. Ищем idSetter на основе локального анализа сессии текущего класса
        final Method targetIdSetter = findIdSetter(cl);
        if (targetIdSetter != null) {
            generateIdSetter(builder, className, targetIdSetter);
        }

        // 3.1 Остальные сеттеры обязаны делегировать во внутренний объект: обертка наследует
        // мертвые поля сущности, и мутирующий их сеттер молча ТЕРЯЛ изменение состояния
        generateDelegatingSetters(builder, ctx, targetIdSetter);

        // 4. Генерируем блоки методов, делегируя контекст
        generateSimpleGetters(builder, ctx);
        generateStrongGetters(builder, ctx);
        generateClosedMethods(builder, ctx);
        generateObjectMethods(builder, className);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
        return code;
    }

    // === АТОМАРНЫЕ УЗЛЫ ДЕКОМПОЗИЦИИ КЛАССА ===

    private void appendPackageAndClassHeader(final StringBuilder builder, final String classesPackage, final String className, final Class<?> cl) {
        builder.append("package ").append(classesPackage).append(";\r\n\r\n");
        builder.append("public class ").append(className).append(" extends ").append(getSourceTypeName(cl));
        builder.append(" implements ").append(IXdStorageIdObservableWrapper.class.getName()).append(" {\r\n");
    }

    private void appendPrivateFields(final StringBuilder builder, final Class<?> cl) {
        builder.append("\r\n\tprivate ").append(getSourceTypeName(cl)).append(" object;\r\n");
        builder.append("\r\n\tprivate java.util.Collection<").append(XdStorageAbstractIdObserver.class.getName())
                .append("> observers = new java.util.ArrayList<>();\r\n");
    }

    private void appendConstructor(final StringBuilder builder, final String className, final Class<?> cl) {
        builder.append("\r\n\tpublic ").append(className).append("(").append(getSourceTypeName(cl)).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t}\r\n");
    }

    private void appendObserverRegistryMethods(final StringBuilder builder) {
        builder.append("\r\n\tpublic void addObserver(final ").append(XdStorageAbstractIdObserver.class.getName()).append(" observer) {\r\n")
                .append("\t\tobservers.add(observer);\r\n").append("\t}\r\n");
    }

    // === ЛОГИКА СБОРКИ КОДОВЫХ БЛОКОВ МЕТОДОВ ===

    private Method findIdSetter(final Class<?> cl) {
        final String idFieldName = getIdFieldName(cl);
        if (idFieldName == null) return null;
        for (final Method method : getPublicMethods(cl)) {
            final String name = method.getName();
            if (name.startsWith("set") && name.length() > 3) {
                final String fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                if (fieldName.equals(idFieldName)) {
                    return method;
                }
            }
        }
        return null;
    }

    private void generateDelegatingSetters(final StringBuilder builder, final GenerationContext ctx, final Method excludedIdSetter) {
        for (Method method : ctx.setters) {
            // рефлекшн возвращает новые копии Method на каждый вызов — сравниваем
            // по сигнатуре, а не по ссылке
            if (excludedIdSetter != null
                    && method.getName().equals(excludedIdSetter.getName())
                    && java.util.Arrays.equals(method.getParameterTypes(), excludedIdSetter.getParameterTypes())) {
                continue;
            }
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tobject.").append(buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n\t}\r\n");
        }
    }

    private void generateIdSetter(final StringBuilder builder, final String className, final Method method) {
        builder.append("\r\n\t@Override\r\n\t");
        builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
        builder.append("\t\tobject.").append(buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
        builder.append("\r\n\t\tobservers.stream().forEach(observer -> {\r\n")
                .append("\t\t\tobserver.onNewIdIsSet(").append(className).append(".this, var0);\r\n")
                .append("\t\t});\r\n\t}\r\n");
    }

    private void generateStrongGetters(final StringBuilder builder, final GenerationContext ctx) {
        for (Method getter : ctx.strongGetters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(), getter.getReturnType(), getter.getGenericReturnType(), getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn object.").append(buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(";\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateClosedMethods(final StringBuilder builder, final GenerationContext ctx) {
        for (Method method : ctx.toCloseMethods) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName()).append("(\"modification of observable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateSimpleGetters(final StringBuilder builder, final GenerationContext ctx) {
        for (Method getter : ctx.fieldsGetters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(), getter.getReturnType(), getter.getGenericReturnType(), getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn object.").append(buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(";\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateObjectMethods(final StringBuilder builder, final String className) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n\t\treturn object.hashCode();\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic Object getWrappedObject__() {\r\n\t\treturn object;\r\n\t}\r\n");

        // =========================================================================
        // ЭТАЛОННЫЙ ФИКС КОДОГЕНЕРАЦИИ REACTIVE WRAPPER (По твоему канону без прикрас):
        // Сравнение идет СТРОГО враппер-к-врапперу. Любой чистый доменный объект или
        // прокси другого типа гарантированно возвращает false, полностью защищая
        // реактивный контур СУБД от утечек симметрии!
        // Распаковка через getWrappedObject__() — без рефлекшна на каждый вызов.
        // =========================================================================
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n");
        builder.append("\t\tif (this == obj) return true;\r\n");
        builder.append("\t\tif (obj == null) return false;\r\n\r\n");

        // 1) Проверяем объект-параметр: является ли он IXdStorageIdObservableWrapper?
        builder.append("\t\tif (obj instanceof ").append(IXdStorageIdObservableWrapper.class.getName()).append(") {\r\n");
        // 1.1) Если да — вытаскиваем обёрнутый объект напрямую и сравниваем через .equals()
        builder.append("\t\t\treturn this.object.equals(((").append(IXdStorageIdObservableWrapper.class.getName()).append(") obj).getWrappedObject__());\r\n");
        builder.append("\t\t}\r\n\r\n");

        // 1.2) Если нет — объекты ГАРАНТИРОВАННО не равны!
        builder.append("\t\treturn false;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n\t\treturn object.toString();\r\n\t}\r\n");
    }

    @Override
    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "IdObservableWrapper";
    }
}
