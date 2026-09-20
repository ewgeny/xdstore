package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.Map;

/**
 * Исправленный и потокобезопасный генератор байт-кода для создания
 * наблюдаемых оберток (Observable Wrappers) над объектами СУБД.
 *
 * <p>Данный класс перехватывает сеттер первичного ключа (ID) и автоматически
 * оповещает зарегистрированных наблюдателей (Observers) при его изменении.
 * Все остальные методы модификации закрыты и выбрасывают исключение.
 */
public class XdStorageObservableWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    public XdStorageObservableWrapperClassCodeGenerator() {
        super(false); // Для observable-оберток ленивая загрузка по get не требуется
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // ИСПРАВЛЕНИЕ RACE CONDITION: Используем изолированный локальный контекст для текущего потока
        final GenerationContext ctx = new GenerationContext();

        collectMethodsLocal(cl, ctx);
        generateClassesCode(classesPackage, cl, code, ctx);

        return code;
    }

    /**
     * Заполняет локальный контекст потока методами интроспекции, специфичными для Observable-обертки.
     */
    @Override
    protected void collectMethodsLocal(final Class<?> cl, final GenerationContext ctx) {
        // Сначала собираем общие группы методов (геттеры, сеттеры, закрытые) через базовый класс
        super.collectMethodsLocal(cl, ctx);

        // ИСПРАВЛЕНИЕ РЕФЛЕКСИИ: Выделяем сеттер ID из общего списка сетеров на основе иерархического поиска полей
        final Method[] methods = getPublicMethods(cl);
        for (final Method method : methods) {
            final String name = method.getName();
            if (name.startsWith("set")) {
                try {
                    final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
                    // Используем исправленный рекурсивный поиск поля вверх по иерархии наследования
                    final Field field = getFieldHierarchy(cl, fieldName);

                    if (field.isAnnotationPresent(XdStorageObjectId.class)) {
                        ctx.idSetter = method;
                        // Сеттер ID не должен блокировать транзакции, убираем его из списка закрытых мутаторов
                        ctx.toCloseMethods.remove(method);
                        ctx.setters.remove(method);
                    }
                } catch (final NoSuchFieldException e) {
                    log.debug("Поле для метода " + name + " не найдено в текущем классе, проверяем иерархию", e);
                }
            }
        }
    }

    private void generateClassesCode(final String classesPackage, final Class<?> cl, final Map<String, String> code, final GenerationContext ctx) {
        final StringBuilder builder = new StringBuilder();

        builder.append("package ").append(classesPackage).append(";\r\n\r\n");

        final String className = buildClassName(classesPackage, cl);
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageIdObservableWrapper.class.getName()).append(" {\r\n");

        // Приватные поля обертки
        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate java.util.Collection<").append(XdStorageAbstractIdObserver.class.getName())
                .append("> observers = new java.util.ArrayList<>();\r\n");

        // Конструктор
        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(cl.getName()).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t}\r\n");

        // Метод регистрации наблюдателей
        builder.append("\r\n\tpublic void addObserver(final ").append(XdStorageAbstractIdObserver.class.getName()).append(" observer) {\r\n")
                .append("\t\tobservers.add(observer);\r\n").append("\t}\r\n");

        // Генерация специфичного сеттера ID с триггером оповещения наблюдателей
        generateIdSetter(builder, className, ctx);

        // Генерация прозрачных геттеров простых типов данных
        generateSimpleGetters(builder, ctx);

        // Генерация геттеров сложных рефлексивных объектов
        generateStrongGetters(builder, ctx);

        // Генерация методов, блокирующих изменение бизнес-свойств
        generateClosedMethods(builder, ctx);

        // Переопределение базовых методов java.lang.Object
        generateObjectMethods(builder, className);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
    }

    private void generateIdSetter(final StringBuilder builder, final String className, final GenerationContext ctx) {
        if (ctx.idSetter == null) {
            return;
        }

        final Method method = ctx.idSetter;
        final int modifiers = method.getModifiers();
        final TypeVariable<Method>[] typeParameters = method.getTypeParameters();
        final Class<?> returnType = method.getReturnType();
        final Type genReturnType = method.getGenericReturnType();
        final String name = method.getName();
        final Class<?>[] parameterTypes = method.getParameterTypes();

        builder.append("\r\n\t@Override\r\n\t");
        builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
        builder.append("\t\tobject.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");

        // Цикл оповещения всех слушателей об изменении первичного ключа
        builder.append("\r\n\t\tobservers.stream().forEach(observer -> {\r\n")
                .append("\t\t\tobserver.onNewIdIsSet(").append(className).append(".this, var0);\r\n")
                .append("\t\t});\r\n");
        builder.append("\t}\r\n");
    }

    private void generateSimpleGetters(final StringBuilder builder, final GenerationContext ctx) {
        for (Method getter : ctx.fieldsGetters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(), getter.getReturnType(), getter.getGenericReturnType(), getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn object.").append(buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(";\r\n");
            builder.append("\t}\r\n");
        }
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
        // Все остальные методы мутации выбрасывают ошибку модификации в observable режиме
        for (Method method : ctx.toCloseMethods) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"Modification of observable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateObjectMethods(final StringBuilder builder, final String className) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n");
        builder.append("\t\treturn 1;\r\n"); // Согласно контракту оригинального wrappers-класса
        builder.append("\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n");
        builder.append("\t\tif (this == obj) return true;\r\n");
        builder.append("\t\tif (obj == null || getClass() != obj.getClass()) return false;\r\n");
        builder.append("\t\tfinal ").append(className).append(" that = (").append(className).append(") obj;\r\n");
        builder.append("\t\treturn object == that.object;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n");
        builder.append("\t\treturn object.toString();\r\n");
        builder.append("\t}\r\n");
    }

    @Override
    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "IdObservableWrapper";
    }
}
