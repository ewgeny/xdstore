package org.flib.xdstorage.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.annotations.XdStorageLoadByGetMethod;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageParentObject;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Базовый абстрактный класс для динамических генераторов исходного кода прокси-оберток.
 *
 * <p>Класс спроектирован в рамках инварианта Stateless (без сохранения состояния в полях)
 * для обеспечения абсолютной потокобезопасности во время параллельной кодогенерации
 * в конкурентных воркерах транзакций СУБД.
 */
public abstract class XdStorageAbstractClassCodeGenerator {

    protected static final Logger log = LogManager.getLogger(XdStorageAbstractClassCodeGenerator.class);

    protected final boolean generateLoadByGet;

    protected XdStorageAbstractClassCodeGenerator() {
        this(false);
    }

    protected XdStorageAbstractClassCodeGenerator(final boolean generateLoadByGet) {
        this.generateLoadByGet = generateLoadByGet;
    }

    /**
     * Основной контракт для генерации строкового представления исходного кода класса.
     */
    public abstract Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code);

    /**
     * ИСПРАВЛЕНИЕ ОШИБКИ РЕФЛЕКСИИ: Выполняет рекурсивный поиск поля по всей иерархии
     * наследования классов СУБД, включая базовые абстрактные сущности.
     */
    protected Field getFieldHierarchy(final Class<?> cl, final String fieldName) throws NoSuchFieldException {
        Class<?> current = cl;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass(); // Поднимаемся к родителю
            }
        }
        throw new NoSuchFieldException("Поле " + fieldName + " не найдено в иерархии класса " + cl.getName());
    }

    /**
     * Контейнер локального контекста потока для хранения списков методов (Паттерн Context).
     * Защищает многопоточную компиляцию от Race Conditions.
     */
    public static class GenerationContext {
        public Method parentGetter;
        public final List<Method> fieldsGetters = new ArrayList<>();
        public final List<Method> strongGetters = new ArrayList<>();
        public final List<Method> setters = new ArrayList<>();
        public final List<Method> strongLoadByGetGetters = new ArrayList<>();
        public final List<Method> toCloseMethods = new ArrayList<>();
        public Method idSetter;
    }

    /**
     * Выполняет потокобезопасный сбор открытых методов класса и их классификацию.
     */
    protected void collectMethodsLocal(final Class<?> cl, final GenerationContext ctx) {
        final Method[] methods = getPublicMethods(cl);
        for (final Method method : methods) {
            final String name = method.getName();
            final Class<?> tmp = method.getReturnType();
            try {
                if (name.startsWith("get")) {
                    if (XdStorageObjectUtils.isSimpleType(tmp, null)) {
                        ctx.fieldsGetters.add(method);
                    } else {
                        if (generateLoadByGet && isLoadByGet(cl, method)) {
                            ctx.strongLoadByGetGetters.add(method);
                        } else {
                            ctx.strongGetters.add(method);
                        }
                    }
                    if (isParentGetter(cl, method)) {
                        ctx.parentGetter = method;
                    }
                } else if (name.startsWith("is")) {
                    if (XdStorageObjectUtils.isSimpleType(tmp, null)) {
                        ctx.fieldsGetters.add(method);
                    } else {
                        ctx.strongGetters.add(method);
                    }
                } else if (name.startsWith("set")) {
                    ctx.setters.add(method);
                } else if (!name.equals("equals") && !name.equals("hashCode") && !name.equals("toString")) {
                    ctx.toCloseMethods.add(method);
                }
            } catch (Exception e) {
                log.warn("Ошибка интроспекции метода " + name + " в локальном контексте", e);
            }
        }
    }

    protected String getIdFieldName(final Class<?> cl) {
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            final Field[] tmp = clazz.getDeclaredFields();
            for (final Field field : tmp) {
                if (field.getAnnotation(XdStorageObjectId.class) != null) {
                    return field.getName();
                }
            }
        }
        return null;
    }

    protected boolean isParentGetter(final Class<?> cl, final Method method) {
        try {
            final String name = method.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
            final Field field = getFieldHierarchy(cl, fieldName); // На базе иерархического поиска
            return field.isAnnotationPresent(XdStorageParentObject.class);
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isLoadByGet(final Class<?> cl, final Method method) {
        try {
            final String name = method.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
            final Field field = getFieldHierarchy(cl, fieldName); // На базе иерархического поиска
            return field.isAnnotationPresent(XdStorageLoadByGetMethod.class);
        } catch (final Exception e) {
            return false;
        }
    }

    protected static Method[] getPublicMethods(final Class<?> cl) {
        final List<Method> result = new ArrayList<>();
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            final Method[] tmp = clazz.getDeclaredMethods();
            for (final Method method : tmp) {
                if ((method.getModifiers() & Modifier.PUBLIC) > 0) {
                    result.add(method);
                }
            }
        }
        return result.toArray(new Method[0]);
    }

    /**
     * Конструирует строковую Java-сигнатуру метода со всеми генериками, аргументами и исключениями.
     */
    protected String buildMethodDefinition(final int modifiers, final String name, final TypeVariable<Method>[] typeParameters, final Class<?> returnType,
                                           final Type genReturnType, final Class<?>[] parameterTypes, final Type[] genParameterTypes,
                                           final Class<?>[] exceptionTypes) {
        final StringBuilder builder = new StringBuilder();

        builder.append("public ");
        if (typeParameters != null && typeParameters.length > 0) {
            builder.append("<");
            for (int i = 0; i < typeParameters.length; ++i) {
                builder.append(typeParameters[i].toString());
                if (i < typeParameters.length - 1) builder.append(", ");
            }
            builder.append("> ");
        }

        if (genReturnType != null && (genReturnType instanceof ParameterizedType || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else if (returnType.isArray()) {
            builder.append(returnType.getComponentType().getName()).append("[]");
        } else {
            builder.append(returnType.getName());
        }

        builder.append(" ").append(name).append("(");

        Class<?>[] activeParams = parameterTypes != null ? parameterTypes : new Class<?>[0];
        for (int i = 0; i < activeParams.length; ++i) {
            String parameterTypeName = activeParams[i].isArray() ? activeParams[i].getComponentType().getName() + "[]" : activeParams[i].getName();
            builder.append(parameterTypeName).append(" var").append(i);
            if (i < activeParams.length - 1) builder.append(", ");
        }
        builder.append(")");

        if (exceptionTypes != null && exceptionTypes.length > 0) {
            builder.append(" throws ");
            for (int i = 0; i < exceptionTypes.length; ++i) {
                builder.append(exceptionTypes[i].getName());
                if (i < exceptionTypes.length - 1) builder.append(',');
            }
        }

        return builder.toString();
    }

    /**
     * Формирует строковый вызов оригинального метода с подстановкой var0, var1 и т.д.
     */
    String buildMethodCalling(final String name, final Class<?>[] parameterTypes) {
        final StringBuilder builder = new StringBuilder();
        builder.append(name).append("(");
        int len = parameterTypes != null ? parameterTypes.length : 0;
        for (int i = 0; i < len; ++i) {
            builder.append("var").append(i);
            if (i < len - 1) builder.append(", ");
        }
        builder.append(")");
        return builder.toString();
    }

    protected abstract String buildClassName(final String classesPackage, final Class<?> cl);
}
