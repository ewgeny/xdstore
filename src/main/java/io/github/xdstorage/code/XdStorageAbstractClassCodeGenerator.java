package io.github.xdstorage.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.xdstorage.annotations.XdStorageLoadByGetMethod;
import io.github.xdstorage.annotations.XdStorageObjectId;
import io.github.xdstorage.annotations.XdStorageParentObject;
import io.github.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Полностью stateless базовый генератор кода (Поинт В / Г).
 * Исключает изменяемые поля класса для предотвращения затирания данных при рекурсивной генерации прокси.
 */
public abstract class XdStorageAbstractClassCodeGenerator {

    protected static final Logger log = LogManager.getLogger(XdStorageAbstractClassCodeGenerator.class);
    protected final boolean generateLoadByGet;

    /**
     * Контекст собираемых методов, изолированный в рамках одного вызова.
     */
    protected static class GenerationContext {
        public Method parentGetter;
        public final List<Method> fieldsGetters = new ArrayList<>();
        public final List<Method> strongGetters = new ArrayList<>();
        public final List<Method> setters = new ArrayList<>();
        public final List<Method> strongLoadByGetGetters = new ArrayList<>();
        public final List<Method> toCloseMethods = new ArrayList<>();
    }

    protected XdStorageAbstractClassCodeGenerator() {
        this(false);
    }

    protected XdStorageAbstractClassCodeGenerator(final boolean generateLoadByGet) {
        this.generateLoadByGet = generateLoadByGet;
    }

    public abstract Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code);

    /**
     * Возвращает имя класса в форме, пригодной для подстановки в генерируемый исходный код Java.
     * Бинарное имя вложенного класса (Outer$Inner) не разрешается компилятором в исходнике,
     * поэтому member-классы обязаны эмититься через точку (Outer.Inner).
     */
    protected static String getSourceTypeName(final Class<?> cl) {
        if (cl.isArray()) {
            return getSourceTypeName(cl.getComponentType()) + "[]";
        }
        if (!cl.isMemberClass()) {
            return cl.getName();
        }
        return getSourceTypeName(cl.getEnclosingClass()) + "." + cl.getSimpleName();
    }

    protected String buildMethodDefinition(final int modifiers, final String name, final TypeVariable<Method>[] typeParameters, final Class<?> returnType,
                                           final Type genReturnType, final Class<?>[] parameterTypes, final Type[] genParameterTypes,
                                           final Class<?>[] exceptionTypes) {
        final StringBuilder builder = new StringBuilder();
        builder.append("public ");
        if (typeParameters != null && typeParameters.length > 0) {
            builder.append("<");
            for (int i = 0; i < typeParameters.length; ++i) {
                builder.append(typeParameters[i].toString());
                if (i < typeParameters.length - 1) {
                    builder.append(", ");
                }
            }
            builder.append("> ");
        }
        if (genReturnType != null && (genReturnType instanceof ParameterizedType || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else {
            builder.append(getSourceTypeName(returnType));
        }
        builder.append(" ").append(name).append("(");
        if (genParameterTypes != null && genParameterTypes.length > 0) {
            for (int i = 0; i < genParameterTypes.length; ++i) {
                final Type type = genParameterTypes[i];
                if (type instanceof Class<?>) {
                    builder.append(getSourceTypeName(parameterTypes[i])).append(" ").append("var").append(i);
                } else if (type instanceof ParameterizedType || type instanceof TypeVariable) {
                    builder.append(type.toString()).append(" ").append("var").append(i);
                }
                if (i < parameterTypes.length - 1) {
                    builder.append(", ");
                }
            }
        } else {
            for (int i = 0; i < parameterTypes.length; ++i) {
                builder.append(getSourceTypeName(parameterTypes[i])).append(" ").append("var").append(i);
                if (i < parameterTypes.length - 1) {
                    builder.append(", ");
                }
            }
        }
        builder.append(")");

        if (exceptionTypes.length > 0) {
            builder.append(" throws ");
            for(int i = 0; i < exceptionTypes.length; ++i) {
                builder.append(getSourceTypeName(exceptionTypes[i]));
                if (i < exceptionTypes.length - 1) {
                    builder.append(',');
                }
            }
        }
        return builder.toString();
    }

    protected String buildMethodCalling(final String name, final Class<?>[] parameterTypes) {
        final StringBuilder builder = new StringBuilder();
        builder.append(name).append("(");
        for (int i = 0; i < parameterTypes.length; ++i) {
            builder.append("var").append(i);
            if (i < parameterTypes.length - 1) {
                builder.append(", ");
            }
        }
        builder.append(")");
        return builder.toString();
    }

    protected GenerationContext collectMethodsAndClasses(final Class<?> cl) {
        final GenerationContext ctx = new GenerationContext();
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
                    try {
                        if (isParentGetter(cl, method)) {
                            ctx.parentGetter = method;
                        }
                    } catch (final NoSuchFieldException e) {
                        log.debug("field was not found", e);
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
                log.warn("collection methods and classes error", e);
            }
        }
        return ctx;
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

    protected boolean isParentGetter(final Class<?> cl, final Method method) throws NoSuchFieldException {
        final String name = method.getName();
        final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
        final Field field = cl.getDeclaredField(fieldName);
        return field.isAnnotationPresent(XdStorageParentObject.class);
    }

    protected boolean isLoadByGet(final Class<?> cl, final Method method) {
        try {
            final String name = method.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
            final Field field = cl.getDeclaredField(fieldName);
            return field.isAnnotationPresent(XdStorageLoadByGetMethod.class);
        } catch(final Exception e) {
            return false;
        }
    }

    protected static Method[] getPublicMethods(final Class<?> cl) {
        final List<Method> result = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            final Method[] tmp = clazz.getDeclaredMethods();
            for (final Method method : tmp) {
                final int modifiers = method.getModifiers();
                if ((modifiers & Modifier.PUBLIC) == 0) continue;
                // static/final методы нельзя переопределить в генерируемой обертке — их эмиссия
                // ломает компиляцию и молча деградирует обертку в dummy; synthetic (bridge) методы
                // дублируют свою целевую декларацию
                if ((modifiers & (Modifier.STATIC | Modifier.FINAL)) != 0 || method.isSynthetic()) continue;
                // обход идет от наследника вверх: первая декларация — самая производная,
                // повторное переопределение в предке не эмитится вторым копией метода
                if (!seen.add(method.getName() + java.util.Arrays.toString(method.getParameterTypes()))) continue;
                result.add(method);
            }
        }
        return result.toArray(new Method[result.size()]);
    }

    protected abstract String buildClassName(final String classesPackage, final Class<?> cl);
}
