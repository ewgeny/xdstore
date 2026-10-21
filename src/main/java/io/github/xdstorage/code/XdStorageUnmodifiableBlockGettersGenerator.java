package io.github.xdstorage.code;

import io.github.xdstorage.utils.XdStorageObjectUtils;
import io.github.xdstorage.exceptions.XdStorageConnectionException;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageRuntimeException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Изолированный компонент декомпозиции (Поинт В).
 * Отвечает за генерацию геттеров и ленивой загрузки для unmodifiable-оберток.
 */
public class XdStorageUnmodifiableBlockGettersGenerator {

    public static void generateSimpleGetters(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method getter : ctx.fieldsGetters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(), getter.getReturnType(), getter.getGenericReturnType(), getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn object.").append(gen.buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(";\r\n\t}\r\n");
        }
    }

    public static void generateStrongGetters(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method getter : ctx.strongGetters) {
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(), returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");

            if (getter == ctx.parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n\t\t\treturn (");
                buildTypeCast(builder, genReturnType, returnType);
                builder.append(")parent;\r\n\t\t} else {\r\n\t\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(");
                builder.append("object.").append(gen.buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(", storage, transaction);\r\n\t\t}\r\n");
            } else {
                builder.append("\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(this, ");
                builder.append("object.").append(gen.buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(", storage, transaction);\r\n");
            }
            builder.append("\t}\r\n");
        }
    }

    public static void generateLoadByGetGetters(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method getter : ctx.strongLoadByGetGetters) {
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);

            builder.append("\r\n\tprivate boolean ").append(fieldName).append("Loaded;\r\n");
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(getter.getModifiers(), name, getter.getTypeParameters(), returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");

            builder.append("\t\tif(!").append(fieldName).append("Loaded) {\r\n\t\t\ttry{\r\n");
            // null-стража: пустое ленивое поле не должно превращаться в storage.load(null)
            // (семейство simple-оберток делает ту же проверку)
            builder.append("\t\t\t\tObject toLoadObject = object.").append(gen.buildMethodCalling(name, getter.getParameterTypes())).append(";\r\n");
            builder.append("\t\t\t\tif (toLoadObject != null) {\r\n\t\t\t\t\tstorage.load(toLoadObject, transaction);\r\n\t\t\t\t}\r\n");
            builder.append("\t\t\t\t").append(fieldName).append("Loaded = true;\r\n");
            builder.append("\t\t\t} catch(").append(XdStorageConnectionException.class.getName()).append(" | ").append(XdStorageException.class.getName()).append(" e) {\r\n");
            builder.append("\t\t\t\ttransaction.markRollbackOnly();\r\n");
            builder.append("\t\t\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"couldn't load objects of field ").append(fieldName).append(", transaction \" + transaction.getTransactionId() + \" marked as rollback only\");\r\n");
            builder.append("\t\t\t}\r\n\t\t}\r\n");

            if (getter == ctx.parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n\t\t\treturn (");
                buildTypeCast(builder, genReturnType, returnType);
                builder.append(")parent;\r\n\t\t} else {\r\n\t\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(");
                builder.append("object.").append(gen.buildMethodCalling(name, getter.getParameterTypes())).append(", storage, transaction);\r\n\t\t}\r\n");
            } else {
                builder.append("\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(this, ");
                builder.append("object.").append(gen.buildMethodCalling(name, getter.getParameterTypes())).append(", storage, transaction);\r\n");
            }
            builder.append("\t}\r\n");
        }
    }

    private static void buildTypeCast(StringBuilder builder, Type genReturnType, Class<?> returnType) {
        if (genReturnType != null && (genReturnType instanceof ParameterizedType || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else {
            builder.append(XdStorageAbstractClassCodeGenerator.getSourceTypeName(returnType));
        }
    }
}
