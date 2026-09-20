package org.flib.xdstorage.code;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class XdStorageSimpleWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    public XdStorageSimpleWrapperClassCodeGenerator() {
        super(true);
    }

    @Override
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
        builder.append(" implements ").append(IXdStorageSimpleWrapper.class.getName()).append(" {\r\n");

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
        builder.append(IXdStorageTransaction.class.getName()).append(" transaction");
        builder.append(") {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t\tthis.parent = parent;\r\n");
        builder.append("\t\tthis.storage = storage;\r\n");
        builder.append("\t\tthis.transaction = transaction;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\tpublic static boolean isDummySimpleWrapper() {\r\n");
        builder.append("\t\treturn false;\r\n");
        builder.append("\t}\r\n");

        generateSimpleWrapperMethods(builder, cl, code);

        generateSimpleGetters(builder, code);

        generateStrongGetters(builder, code);

        generateLoadByGetGetters(builder, code);

        generateSettersMethods(builder, code);

        generateClosedMethods(builder, code);

        generateObjectMethods(builder, code);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
    }

    private void generateSimpleWrapperMethods(final StringBuilder builder, final Class<?> cl, final Map<String, String> code) {
        builder.append("\r\n\t").append("@Override");
        builder.append("\r\n\tpublic Object getObjectId__() {");
        builder.append("\r\n\t\treturn object.").append(getObjectGetIdMethodName(cl)).append("();");
        builder.append("\r\n\t}\r\n");

        builder.append("\r\n\t").append("@Override");
        builder.append("\r\n\tpublic void setReference__(boolean reference__) {");
        builder.append("\r\n\t\tthis.reference__ = reference__;");
        builder.append("\r\n\t}\r\n");

        builder.append("\r\n\t").append("@Override");
        builder.append("\r\n\tpublic boolean isReference__() {");
        builder.append("\r\n\t\treturn this.reference__;");
        builder.append("\r\n\t}\r\n");

        builder.append("\r\n\t").append("@Override");
        builder.append("\r\n\tpublic void lock__() {");
        builder.append("\r\n\t\tlock__.lock();");
        builder.append("\r\n\t}\r\n");

        builder.append("\r\n\t").append("@Override");
        builder.append("\r\n\tpublic void unlock__() {");
        builder.append("\r\n\t\tlock__.unlock();");
        builder.append("\r\n\t}\r\n");
    }

    private String getObjectGetIdMethodName(final Class<?> cl) {
        final String idFieldName = getIdFieldName(cl);
        return "get" + Character.toUpperCase(idFieldName.charAt(0)) + idFieldName.substring(1);
    }

    private void generateLoadByGetGetters(final StringBuilder builder, final Map<String, String> code) {
        for (Method getter : strongLoadByGetGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);
            final Class<?>[] parameterTypes = getter.getParameterTypes();
            final Type[] genParameterTypes = getter.getGenericParameterTypes();
            final Class<?>[] exceptionTypes = getter.getExceptionTypes();

            builder.append("\r\n\tprivate boolean ").append(fieldName).append("Loaded;\r\n");

            builder.append("\r\n\t").append("@Override").append("\r\n\t");
            builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, parameterTypes, genParameterTypes, exceptionTypes)).append(" {\r\n");

            builder.append("\t\tif(!").append(fieldName).append("Loaded) {\r\n");
            builder.append("\t\t\ttry{\r\n");

            if (returnType.isArray()) {
                builder.append("\t\t\t\t").append(returnType.getComponentType().getName()).append("[] toLoadCollection = object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t\t\tif (toLoadCollection != null) {\r\n");
                builder.append("\t\t\t\t\tfor (Object toLoadObject : toLoadCollection) {\r\n");
                builder.append("\t\t\t\t\t\tif (toLoadObject != null) {\r\n");
                builder.append("\t\t\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n");
                builder.append("\t\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t}\r\n");
            } else if (returnType.isAssignableFrom(Collection.class)) {
                builder.append("\t\t\t\t").append(Collection.class.getName()).append("<? extends Object> toLoadCollection = object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t\t\tif (toLoadCollection != null) {\r\n");
                builder.append("\t\t\t\t\tfor (Object toLoadObject : toLoadCollection) {\r\n");
                builder.append("\t\t\t\t\t\tif (toLoadObject != null && ").append(XdStorageObjectUtils.class.getName()).append(".isSimpleWrappedObject(toLoadObject)) {\r\n");
                builder.append("\t\t\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n");
                builder.append("\t\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t}\r\n");
            } else if (returnType.isAssignableFrom(List.class)) {
                builder.append("\t\t\t\t").append(List.class.getName()).append("<? extends Object> toLoadCollection = object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t\t\tif (toLoadCollection != null) {\r\n");
                builder.append("\t\t\t\t\tfor (Object toLoadObject : toLoadCollection) {\r\n");
                builder.append("\t\t\t\t\t\tif (toLoadObject != null && ").append(XdStorageObjectUtils.class.getName()).append(".isSimpleWrappedObject(toLoadObject)) {\r\n");
                builder.append("\t\t\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n");
                builder.append("\t\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t}\r\n");
            } else if (returnType.isAssignableFrom(Map.class)) {
                builder.append("\t\t\t\t").append(Collection.class.getName()).append("<? extends Object> toLoadCollection = object.").append(buildMethodCalling(name, parameterTypes)).append(".values();\r\n");
                builder.append("\t\t\t\tif (toLoadCollection != null) {\r\n");
                builder.append("\t\t\t\t\tfor (Object toLoadObject : toLoadCollection) {\r\n");
                builder.append("\t\t\t\t\t\tif (toLoadObject != null && ").append(XdStorageObjectUtils.class.getName()).append(".isSimpleWrappedObject(toLoadObject)) {\r\n");
                builder.append("\t\t\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n");
                builder.append("\t\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t\t}\r\n");
                builder.append("\t\t\t\t}\r\n");
            } else {
                builder.append("\t\t\t\tObject toLoadObject = object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t\t\tif (toLoadObject != null && ").append(XdStorageObjectUtils.class.getName()).append(".isSimpleWrappedObject(toLoadObject)) {\r\n");
                builder.append("\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n");
                builder.append("\t\t\t\t}\r\n");
            }

            builder.append("\t\t\t\t").append(fieldName).append("Loaded = true;\r\n");

            builder.append("\t\t\t} catch(").append(Throwable.class.getName()).append(" e) {\r\n");

            builder.append("\t\t\t\ttransaction.markRollbackOnly();\r\n");
            builder.append("\t\t\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"couldn't load objects of field ").append(fieldName);
            builder.append(", transaction \" + transaction.getTransactionId() + \" marked as rollback only\", e);\r\n");

            builder.append("\t\t\t}\r\n");
            builder.append("\t\t}\r\n");

            if (getter == parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n");
                builder.append("\t\t\treturn (");
                if (genReturnType != null
                        && (genReturnType instanceof ParameterizedType
                        || genReturnType instanceof TypeVariable)) {
                    builder.append(genReturnType.toString());
                } else if (returnType.isArray()) {
                    builder.append(returnType.getComponentType().getName()).append("[]");
                } else {
                    builder.append(returnType.getName());
                }
                builder.append(")parent;\r\n");
                builder.append("\t\t} else {\r\n");
                builder.append("\t\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t}\r\n");
            } else {
                builder.append("\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
            }
            builder.append("\t}\r\n");
        }
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
            if (getter == parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n");
                builder.append("\t\t\treturn (");
                if (genReturnType != null
                        && (genReturnType instanceof ParameterizedType
                        || genReturnType instanceof TypeVariable)) {
                    builder.append(genReturnType.toString());
                } else if (returnType.isArray()) {
                    builder.append(returnType.getComponentType().getName()).append("[]");
                } else {
                    builder.append(returnType.getName());
                }
                builder.append(")parent;\r\n");
                builder.append("\t\t} else {\r\n");
                builder.append("\t\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
                builder.append("\t\t}\r\n");
            } else {
                builder.append("\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
            }
            builder.append("\t}\r\n");
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
            builder.append("\t\tobject.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
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
            if ("clone".equalsIgnoreCase(name) || (returnType != null && returnType != void.class)) {
                builder.append("\t\treturn object.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
            } else {
                builder.append("\t\tobject.").append(buildMethodCalling(name, parameterTypes)).append(";\r\n");
            }
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
        return cl.getSimpleName() + "SimpleWrapper";
    }
}
