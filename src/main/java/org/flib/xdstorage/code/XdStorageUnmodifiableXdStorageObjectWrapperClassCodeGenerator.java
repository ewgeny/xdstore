package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageParentObject;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.Map;

/**
 * Исправленный stateless генератор для Unmodifiable XdStorage объектов (Поинт В / Г).
 * Полностью увязан с изолированным контекстом GenerationContext нити.
 */
public class XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // 1. Собираем методы во внутренний изолированный GenerationContext нити
        final GenerationContext ctx = collectMethodsAndClasses(cl);

        final StringBuilder builder = new StringBuilder();
        final String className = buildClassName(classesPackage, cl);

        builder.append("package ").append(classesPackage).append(";\r\n\r\n");
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageUnmodifiableWrapper.class.getName()).append(" {\r\n");

        builder.append("\r\n\tprivate ").append(XdStorageIdentifiableObject.class.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate ").append(Object.class.getName()).append(" parent;\r\n");

        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(Object.class.getName()).append(" parent, ");
        builder.append(XdStorageIdentifiableObject.class.getName()).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t\tthis.parent = parent;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\tpublic static boolean isDummyUnmodifiableWrapper() {\r\n\t\treturn false;\r\n\t}\r\n");

        // 2. Генерируем блоки методов, делегируя контекст ctx наружу
        generateSimpleGetters(builder, ctx);
        generateStrongGetters(builder, ctx);
        generateSettersMethods(builder, ctx);
        generateClosedMethods(builder, ctx);
        generateObjectMethods(builder);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
        return code;
    }

    private void generateStrongGetters(final StringBuilder builder, final GenerationContext ctx) {
        for (Method getter : ctx.strongGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = extractFieldName(getter);

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            if (getter == ctx.parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n\t\t\treturn (");
                buildReturnType(builder, genReturnType, returnType);
                builder.append(")parent;\r\n\t\t} else {\r\n\t\t\treturn (");
                buildReturnType(builder, genReturnType, returnType);
                builder.append(")").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(");
                builder.append("object.getProperty(\"").append(fieldName).append("\"));\r\n\t\t}\r\n");
            } else {
                builder.append("\t\treturn (");
                buildReturnType(builder, genReturnType, returnType);
                builder.append(")").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(this, ");
                builder.append("object.getProperty(\"").append(fieldName).append("\"));\r\n");
            }
            builder.append("\t}\r\n");
        }
    }

    private void generateSimpleGetters(final StringBuilder builder, final GenerationContext ctx) {
        for (Method getter : ctx.fieldsGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = extractFieldName(getter);

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn (");
            buildReturnType(builder, genReturnType, returnType);
            builder.append(")object.getProperty(\"").append(fieldName).append("\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateSettersMethods(final StringBuilder builder, final GenerationContext ctx) {
        for (Method method : ctx.setters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName()).append("(\"modification of viewable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateClosedMethods(final StringBuilder builder, final GenerationContext ctx) {
        for (Method method : ctx.toCloseMethods) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName()).append("(\"modification of viewable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateObjectMethods(final StringBuilder builder) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n\t\treturn object.hashCode();\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n\t\treturn object.equals(obj);\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n\t\treturn object.toString();\r\n\t}\r\n");
    }

    private String extractFieldName(final Method getter) {
        final String getterName = getter.getName();
        if (getterName.startsWith("get")) {
            return getterName.substring(3, 4).toLowerCase() + getterName.substring(4);
        } else {
            return getterName.substring(2, 3).toLowerCase() + getterName.substring(3);
        }
    }

    private void buildReturnType(StringBuilder builder, Type genReturnType, Class<?> returnType) {
        if (genReturnType != null && (genReturnType instanceof ParameterizedType || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else if (returnType.isArray()) {
            builder.append(returnType.getComponentType().getName()).append("[]");
        } else {
            builder.append(returnType.getName());
        }
    }

    @Override
    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "UnmodifiableXdStorageObjectWrapper";
    }
}
