package org.flib.xdstorage.code;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;

/**
 * Специализированный генератор байт-кода для создания неизменяемых оберток (Unmodifiable Wrappers)
 * над идентифицируемыми объектами СУБД (XdStorageIdentifiableObject).
 *
 * <p>Все методы модификации (сеттеры и закрытые методы) генерируют исключение XdStorageRuntimeException,
 * предотвращая случайное изменение данных при операциях чтения.
 */
public class XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    private static final Logger log = LogManager.getLogger(XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator.class);

    public XdStorageUnmodifiableXdStorageObjectWrapperClassCodeGenerator() {
        super(false); // Для unmodifiable-оберток ленивая загрузка по get не требуется
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        // ИСПРАВЛЕНИЕ RACE CONDITION: Используем изолированный локальный контекст для текущего потока
        final GenerationContext ctx = new GenerationContext();

        collectMethodsLocal(cl, ctx);
        generateClassesCode(classesPackage, cl, code, ctx);

        return code;
    }

    private void generateClassesCode(final String classesPackage, final Class<?> cl, final Map<String, String> code, final GenerationContext ctx) {
        final StringBuilder builder = new StringBuilder();

        builder.append("package ").append(classesPackage).append(";\r\n\r\n");

        final String className = buildClassName(classesPackage, cl);
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageUnmodifiableWrapper.class.getName()).append(" {\r\n");

        builder.append("\r\n\tprivate ").append(XdStorageIdentifiableObject.class.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate ").append(Object.class.getName()).append(" parent;\r\n");

        // Генерация конструктора обертки
        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(Object.class.getName()).append(" parent, ");
        builder.append(XdStorageIdentifiableObject.class.getName()).append(" object) {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t\tthis.parent = parent;\r\n");
        builder.append("\t}\r\n");

        builder.append("\r\n\tpublic static boolean isDummyUnmodifiableWrapper() {\r\n");
        builder.append("\t\treturn false;\r\n");
        builder.append("\t}\r\n");

        // Генерация безопасных методов извлечения данных
        generateSimpleGetters(builder, ctx);
        generateStrongGetters(builder, ctx);

        // Генерация запрещающих методов для мутаций
        generateSettersMethods(builder, ctx);
        generateClosedMethods(builder, ctx);

        // Стандартные методы Object (equals, hashCode, toString)
        generateObjectMethods(builder, ctx);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
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
            generateValidationThrow(builder, method);
        }
    }

    private void generateClosedMethods(final StringBuilder builder, final GenerationContext ctx) {
        for (Method method : ctx.toCloseMethods) {
            generateValidationThrow(builder, method);
        }
    }

    private void generateValidationThrow(final StringBuilder builder, final Method method) {
        final int modifiers = method.getModifiers();
        final TypeVariable<Method>[] typeParameters = method.getTypeParameters();
        final Class<?> returnType = method.getReturnType();
        final Type genReturnType = method.getGenericReturnType();
        final String name = method.getName();

        builder.append("\r\n\t@Override\r\n\t");
        builder.append(buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
        builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
        builder.append("(\"Modification of viewable object is not allowed\");\r\n");
        builder.append("\t}\r\n");
    }

    private void generateObjectMethods(final StringBuilder builder, final GenerationContext ctx) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n");
        builder.append("\t\treturn object.hashCode();\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n");
        builder.append("\t\treturn object.equals(obj);\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n");
        builder.append("\t\treturn object.toString();\r\n\t}\r\n");
    }

    private String extractFieldName(final Method getter) {
        final String getterName = getter.getName();
        if (getterName.startsWith("get")) {
            return getterName.substring(3, 4).toLowerCase() + getterName.substring(4);
        } else {
            return getterName.substring(2, 3).toLowerCase() + getterName.substring(3);
        }
    }

    private void buildReturnType(final StringBuilder builder, final Type genReturnType, final Class<?> returnType) {
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
