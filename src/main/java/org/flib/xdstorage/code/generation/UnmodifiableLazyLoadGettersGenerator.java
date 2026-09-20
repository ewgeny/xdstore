package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class UnmodifiableLazyLoadGettersGenerator implements XdStorageUnmodifiableMethodGenerator {

    @Override
    public void generate(StringBuilder builder, Class<?> cl, GenerationContext ctx, XdStorageMethodBuilderUtils utils) {
        for (Method getter : ctx.strongLoadByGetGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();
            final String fieldName = name.substring(3, 4).toLowerCase() + name.substring(4);

            builder.append("\r\n\tprivate boolean ").append(fieldName).append("Loaded;\r\n");

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");

            builder.append("\t\tif(!").append(fieldName).append("Loaded) {\r\n");
            builder.append("\t\t\ttry{\r\n");
            builder.append("\t\t\t\tstorage.load(object.").append(utils.buildMethodCalling(name, getter.getParameterTypes())).append(", transaction);\r\n");
            builder.append("\t\t\t\t").append(fieldName).append("Loaded = true;\r\n");
            builder.append("\t\t\t} catch(").append(XdStorageConnectionException.class.getName());
            builder.append(" | ").append(XdStorageException.class.getName()).append(" e) {\r\n");
            builder.append("\t\t\t\ttransaction.markRollbackOnly();\r\n");
            builder.append("\t\t\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
            builder.append("(\"couldn't load objects of field ").append(fieldName);
            builder.append(", transaction \" + transaction.getTransactionId() + \" marked as rollback only\");\r\n");
            builder.append("\t\t\t}\r\n");
            builder.append("\t\t}\r\n");

            if (getter == ctx.parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n\t\t\treturn (");
                utils.buildCastType(builder, genReturnType, returnType);
                builder.append(")parent;\r\n\t\t} else {\r\n");
                builder.append("\t\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(");
                builder.append("object.").append(utils.buildMethodCalling(name, getter.getParameterTypes())).append(", storage, transaction);\r\n\t\t}\r\n");
            } else {
                builder.append("\t\treturn ").append(XdStorageObjectUtils.class.getName()).append(".wrapAsUnmodifiableObject(this, ");
                builder.append("object.").append(utils.buildMethodCalling(name, getter.getParameterTypes())).append(", storage, transaction);\r\n");
            }
            builder.append("\t}\r\n");
        }
    }
}
