package org.flib.xdstorage.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.annotations.XdStorageParentObject;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {

        collectMethodsAndClasses(cl);

        generateClassesCode(classesPackage, cl, code);

        return code;
    }

    private void generateClassesCode(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        final StringBuilder builder = new StringBuilder();

        builder.append("package ").append(classesPackage).append(";\r\n\r\n");

        final String className = buildClassName(classesPackage, cl);
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

        builder.append("\r\n\tpublic static boolean isDummyUnmodifiableWrapper() {\r\n");
        builder.append("\t\treturn false;\r\n");
        builder.append("\t}\r\n");

        generateSimpleGetters(builder, code);

        generateStrongGetters(builder, code);

        generateSettersMethods(builder, code);

        generateClosedMethods(builder, code);

        generateObjectMethods(builder, code);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
    }

    private void generateStrongGetters(final StringBuilder builder, final Map<String, String> code) {
        for (Method getter : strongGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = extractFieldName(getter);
            final Class<?>[] parameterTypes = getter.getParameterTypes();
            final Type[] genParameterTypes = getter.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = getter.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            if (getter == parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n");
                builder.append("\t\t\treturn (");

                buildReturnType(builder, genReturnType, returnType);

                builder.append(")parent;\r\n");
                builder.append("\t\t} else {\r\n");
                builder.append("\t\t\treturn (");

                buildReturnType(builder, genReturnType, returnType);

                builder.append(")").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(");
                builder.append("object.getProperty(\"").append(fieldName).append("\"));\r\n");
                builder.append("\t\t}\r\n");
            } else {
                builder.append("\t\treturn (");

                buildReturnType(builder, genReturnType, returnType);

                builder.append(")").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(this, ");
                builder.append("object.getProperty(\"").append(fieldName).append("\"));\r\n");
            }
            builder.append("\t}\r\n");
        }
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
        if (genReturnType != null
                && (genReturnType instanceof ParameterizedType
                || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else if (returnType.isArray()) {
            builder.append(returnType.getComponentType().getName()).append("[]");
        } else {
            builder.append(returnType.getName());
        }
    }

    private void generateSettersMethods(final StringBuilder builder, final Map<String, String> code) {
        for (Method method : setters) {
            final int modifiers = method.getModifiers();
            final TypeVariable<Method>[] typeParameters = method.getTypeParameters();
            final Class<?> returnType = method.getReturnType();
            final Type genReturnType = method.getGenericReturnType();
            final String name = method.getName();
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Type[] genParameterTypes = method.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = method.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"modification of viewable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateClosedMethods(final StringBuilder builder, final Map<String, String> code) {
        for (Method method : toCloseMethods) {
            final int modifiers = method.getModifiers();
            final TypeVariable<Method>[] typeParameters = method.getTypeParameters();
            final Class<?> returnType = method.getReturnType();
            final Type genReturnType = method.getGenericReturnType();
            final String name = method.getName();
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final Type[] genParameterTypes = method.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = method.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"modification of viewable object is not allowed\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateSimpleGetters(final StringBuilder builder, final Map<String, String> code) {
        for (Method getter : fieldsGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = extractFieldName(getter);
            final Class<?>[] parameterTypes = getter.getParameterTypes();
            final Type[] genParameterTypes = getter.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = getter.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            builder.append("\t\treturn (");

            buildReturnType(builder, genReturnType, returnType);

            builder.append(")object.getProperty(\"").append(fieldName).append("\");\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateObjectMethods(final StringBuilder builder, final Map<String, String> code) {
        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public int hashCode() {\r\n");
        builder.append("\t\treturn object.hashCode();\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public boolean equals(Object obj) {\r\n");
        builder.append("\t\treturn object.equals(obj);\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public String toString() {\r\n");
        builder.append("\t\treturn object.toString();\r\n");
        builder.append("\t}\r\n");
    }

    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "UnmodifiableXdStorageObjectWrapper";
    }

}
