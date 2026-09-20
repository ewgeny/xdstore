package org.flib.xdstorage.code;

import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.observing.IXdStorageIdObservableWrapper;
import org.flib.xdstorage.observing.XdStorageAbstractIdObserver;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.*;
import java.util.Map;

public class XdStorageObservableWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    protected Method idSetter;

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
        builder.append(" implements ").append(IXdStorageIdObservableWrapper.class.getName()).append(" {\r\n");

        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate java.util.Collection<").append(XdStorageAbstractIdObserver.class.getName())
                .append("> observers = new java.util.ArrayList<>();\r\n");

        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(cl.getName()).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\tpublic void addObserver(final ").append(XdStorageAbstractIdObserver.class.getName()).append(" observer) {\r\n")
                .append("\t\tobservers.add(observer);\r\n").append("\t}\r\n");

        generateIdSetter(builder, className, code);

        generateSimpleGetters(builder, code);

        generateStrongGetters(builder, code);

        generateClosedMethods(builder, code);

        generateObjectMethods(builder, className, code);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
    }

    private void generateIdSetter(final StringBuilder builder, final String className, final Map<String, String> code) {
        if (idSetter == null) {
            return;
        }

        final Method method = idSetter;
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
        builder.append("\t\tobject.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
        builder.append("\r\n\t\t").append("observers.stream().forEach(observer -> {\r\n")
                .append("\t\t\tobserver.onNewIdIsSet(").append(className).append(".this, var0);\r\n")
                .append("\t\t});\r\n");
        builder.append("\t}\r\n");
    }

    private void generateStrongGetters(final StringBuilder builder, final Map<String, String> code) {
        for (Method getter : strongGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final Class<?>[] parameterTypes = getter.getParameterTypes();
            final Type[] genParameterTypes = getter.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = getter.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            builder.append("\t\treturn ").append("object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
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
            builder.append("(\"modification of observable object is not allowed\");\r\n");
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
            final Class<?>[] parameterTypes = getter.getParameterTypes();
            final Type[] genParameterTypes = getter.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = getter.getExceptionTypes();

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");
            builder.append("\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
            builder.append("\t}\r\n");
        }
    }

    private void generateObjectMethods(final StringBuilder builder, final String className, final Map<String, String> code) {
        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public int hashCode() {\r\n");
        builder.append("\t\treturn 1;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public boolean equals(Object obj) {\r\n");
        builder.append("\t\tif (this == obj) return true;\r\n");
        builder.append("\t\tif (obj == null || getClass() != obj.getClass()) return false;\r\n");
        builder.append("\t\tfinal ").append(className).append(" that = (").append(className).append(") obj;\r\n");
        builder.append("\t\treturn object == that.object;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\t").append("@Override").append("\r\n\t");
        builder.append("public String toString() {\r\n");
        builder.append("\t\treturn object.toString();\r\n");
        builder.append("\t}\r\n");
    }

    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "IdObservableWrapper";
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
                        strongGetters.add(method);
                    }
                } else if (name.startsWith("is")) {
                    if (XdStorageObjectUtils.isSimpleType(tmp, null)) {
                        fieldsGetters.add(method);
                    } else {
                        strongGetters.add(method);
                    }
                } else if (!name.equals("equals")
                        && !name.equals("hashCode")
                        && !name.equals("toString")) {
                    boolean isToClose = true;
                    if (name.startsWith("set")) {
                        try {
                            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
                            final Field field = cl.getDeclaredField(fieldName);
                            if (field.isAnnotationPresent(XdStorageObjectId.class)) {
                                idSetter = method;
                                isToClose = false;
                            }
                        } catch (final NoSuchFieldException e) {
                            log.debug(e);
                        }
                    }
                    if (isToClose) {
                        toCloseMethods.add(method);
                    }
                }
            } catch (Exception e) {
                log.warn("collection methods and classes error", e);
            }
        }
    }
}
