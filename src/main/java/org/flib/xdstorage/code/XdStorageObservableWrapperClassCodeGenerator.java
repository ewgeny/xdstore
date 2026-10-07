package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageObjectId;
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
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageIdObservableWrapper.class.getName()).append(" {\r\n");
    }

    private void appendPrivateFields(final StringBuilder builder, final Class<?> cl) {
        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate java.util.Collection<").append(XdStorageAbstractIdObserver.class.getName())
                .append("> observers = new java.util.ArrayList<>();\r\n");
    }

    private void appendConstructor(final StringBuilder builder, final String className, final Class<?> cl) {
        builder.append("\r\n\tpublic ").append(className).append("(").append(cl.getName()).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t}\r\n");
    }

    private void appendObserverRegistryMethods(final StringBuilder builder) {
        builder.append("\r\n\tpublic void addObserver(final ").append(XdStorageAbstractIdObserver.class.getName()).append(" observer) {\r\n")
                .append("\t\tobservers.add(observer);\r\n").append("\t}\r\n");
    }

    // === ЛОГИКА СБОРКИ КОДОВЫХ БЛОКОВ МЕТОДОВ ===

    private Method findIdSetter(final Class<?> cl) {
        final Method[] methods = getPublicMethods(cl);
        for (final Method method : methods) {
            final String name = method.getName();
            if (name.startsWith("set")) {
                try {
                    final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
                    final Field field = cl.getDeclaredField(fieldName);
                    if (field.isAnnotationPresent(XdStorageObjectId.class)) {
                        return method;
                    }
                } catch (final NoSuchFieldException ignored) {
                    // Игнорируем поля без явного совпадения по имени
                }
            }
        }
        return null;
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

        // =========================================================================
        // ЭТАЛОННЫЙ ФИКС КОДОГЕНЕРАЦИИ RECTIVE WRAPPER (По твоему канону без прикрас):
        // Сравнение идет СТРОГО враппер-к-врапперу. Любой чистый доменный объект или
        // прокси другого типа гарантированно возвращает false, полностью защищая
        // реактивный контур СУБД от утечек симметрии!
        // =========================================================================
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n");
        builder.append("\t\tif (this == obj) return true;\r\n");
        builder.append("\t\tif (obj == null) return false;\r\n\r\n");

        // 1) Проверяем объект-параметр: является ли он IXdStorageIdObservableWrapper?
        builder.append("\t\tif (obj instanceof ").append(IXdStorageIdObservableWrapper.class.getName()).append(") {\r\n");
        // 1.1) Если да — вытаскиваем обёрнутый объект через Java Reflection и сравниваем через .equals()
        builder.append("\t\t\ttry {\r\n");
        builder.append("\t\t\t\tjava.lang.reflect.Field f__ = obj.getClass().getDeclaredField(\"object\");\r\n");
        builder.append("\t\t\t\tf__.setAccessible(true);\r\n");
        builder.append("\t\t\t\tObject otherInner__ = f__.get(obj);\r\n");
        builder.append("\t\t\t\treturn this.object.equals(otherInner__);\r\n");
        builder.append("\t\t\t} catch (Exception e__) {\r\n");
        builder.append("\t\t\t\treturn false;\r\n");
        builder.append("\t\t\t}\r\n");
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
