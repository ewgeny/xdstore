package org.flib.xdstorage.code;

import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import java.lang.reflect.Method;

/**
 * Изолированный компонент декомпозиции (Поинт В).
 * Защищает Snapshot-состояния от случайных изменений воркерами.
 */
public class XdStorageUnmodifiableBlockSettersGenerator {

    public static void generateSettersMethods(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method method : ctx.setters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\ttransaction.markRollbackOnly();\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName()).append("(\"modification of viewable object is not allowed\");\r\n\t}\r\n");
        }
    }

    public static void generateClosedMethods(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method method : ctx.toCloseMethods) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\ttransaction.markRollbackOnly();\r\n");
            builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName()).append("(\"modification of viewable object is not allowed\");\r\n\t}\r\n");
        }
    }
}
