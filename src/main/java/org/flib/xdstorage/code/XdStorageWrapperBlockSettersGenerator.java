package org.flib.xdstorage.code;

import java.lang.reflect.Method;

/**
 * Изолированный компонент декомпозиции (Поинт В).
 * Отвечает исключительно за генерацию мутирующих сеттеров и инфраструктурных методов.
 */
public class XdStorageWrapperBlockSettersGenerator {

    public static void generateSettersMethods(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method method : ctx.setters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\tobject.").append(gen.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n\t}\r\n");
        }
    }

    public static void generateClosedMethods(final StringBuilder builder, final XdStorageAbstractClassCodeGenerator.GenerationContext ctx, final XdStorageAbstractClassCodeGenerator gen) {
        for (Method method : ctx.toCloseMethods) {
            final Class<?> returnType = method.getReturnType();
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(gen.buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), returnType, method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");

            if ("clone".equalsIgnoreCase(method.getName()) || (returnType != null && returnType != void.class)) {
                builder.append("\t\treturn object.").append(gen.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
            } else {
                builder.append("\t\tobject.").append(gen.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
            }
            builder.append("\t}\r\n");
        }
    }
}
