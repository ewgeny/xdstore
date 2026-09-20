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

public abstract class XdStorageAbstractClassCodeGenerator {

    protected static final Logger log = LogManager.getLogger(XdStorageUnmodifiableWrapperClassCodeGenerator.class);

    protected final boolean generateLoadByGet;

    protected Method parentGetter;

    protected List<Method> fieldsGetters = new ArrayList<>();

    protected List<Method> strongGetters = new ArrayList<>();

    protected List<Method> setters = new ArrayList<>();

    protected List<Method> strongLoadByGetGetters = new ArrayList<>();

    protected List<Method> toCloseMethods = new ArrayList<>();

    protected XdStorageAbstractClassCodeGenerator() {
        this(false);
    }

    protected XdStorageAbstractClassCodeGenerator(final boolean generateLoadByGet) {
        this.generateLoadByGet = generateLoadByGet;
    }

    public abstract Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code);

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
        if (genReturnType != null
                && (genReturnType instanceof ParameterizedType
                || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else if (returnType.isArray()) {
            builder.append(returnType.getComponentType().getName()).append("[]");
        } else {
            builder.append(returnType.getName());
        }
        builder.append(" ").append(name).append("(");
        if (genParameterTypes != null && genParameterTypes.length > 0) {
            for (int i = 0; i < genParameterTypes.length; ++i) {
                final Type type = genParameterTypes[i];
                if (type instanceof Class<?>) {
                    final String parameterTypeName = parameterTypes[i].isArray() ? parameterTypes[i].getComponentType().getName() + "[]"
                            : parameterTypes[i].getName();
                    builder.append(parameterTypeName).append(" ").append("var").append(i);
                } else if (type instanceof ParameterizedType || type instanceof TypeVariable) {
                    builder.append(type.toString()).append(" ").append("var").append(i);
                }
                if (i < parameterTypes.length - 1) {
                    builder.append(", ");
                }
            }
        } else {
            for (int i = 0; i < parameterTypes.length; ++i) {
                final String parameterTypeName = parameterTypes[i].isArray() ? parameterTypes[i].getComponentType().getName() + "[]"
                        : parameterTypes[i].getName();
                builder.append(parameterTypeName).append(" ").append("var").append(i);
                if (i < parameterTypes.length - 1) {
                    builder.append(", ");
                }
            }
        }
        builder.append(")");

        if (exceptionTypes.length > 0) {
            builder.append(" throws ");
            for(int i = 0; i < exceptionTypes.length; ++i) {
                builder.append(exceptionTypes[i].getName());
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

    protected void collectMethodsAndClasses(final Class<?> cl) {
        final Method[] methods = getPublicMethods(cl);
        for (final Method method : methods) {
            final String name = method.getName();
            final Class<?> tmp = method.getReturnType();
            try {
                if (name.startsWith("get")) {
                    if (XdStorageObjectUtils.isSimpleType(tmp, null)) {
                        fieldsGetters.add(method);
                    } else {
                        if (generateLoadByGet && isLoadByGet(cl, method)) {
                            strongLoadByGetGetters.add(method);
                        } else {
                            strongGetters.add(method);
                        }
                    }
                    try {
                        if (isParentGetter(cl, method)) {
                            parentGetter = method;
                        }
                    } catch (final NoSuchFieldException e) {
                        log.debug(e);
                    }
                } else if (name.startsWith("is")) {
                    if (XdStorageObjectUtils.isSimpleType(tmp, null)) {
                        fieldsGetters.add(method);
                    } else {
                        strongGetters.add(method);
                    }
                } else if (name.startsWith("set")) {
                    setters.add(method);
                } else if (!name.equals("equals")
                        && !name.equals("hashCode")
                        && !name.equals("toString")) {
                    toCloseMethods.add(method);
                }
            } catch (Exception e) {
                log.warn("collection methods and classes error", e);
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
        final List<Method> result = new ArrayList<Method>();
        for (Class<?> clazz = cl; !clazz.equals(Object.class); clazz = clazz.getSuperclass()) {
            final Method[] tmp = clazz.getDeclaredMethods();
            for (final Method method : tmp) {
                if ((method.getModifiers() & Modifier.PUBLIC) > 0) {
                    result.add(method);
                }
            }
        }
        return result.toArray(new Method[result.size()]);
    }

    protected abstract String buildClassName(final String classesPackage, final Class<?> cl);
}
