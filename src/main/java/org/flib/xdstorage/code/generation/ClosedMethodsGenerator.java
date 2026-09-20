package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия генерации системных и служебных закрытых методов бизнес-логики в обертках.
 */
public class ClosedMethodsGenerator implements XdStorageMethodGroupGenerator {

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        for (Method method : ctx.toCloseMethods) {
            final Class<?> returnType = method.getReturnType();

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(
                    method.getModifiers(),
                    method.getName(),
                    method.getTypeParameters(),
                    returnType,
                    method.getGenericReturnType(),
                    method.getParameterTypes(),
                    method.getGenericParameterTypes(),
                    method.getExceptionTypes()
            )).append(" {\r\n");

            // Если метод возвращает значение (например, clone() или кастомный расчет), проксируем результат
            if ("clone".equalsIgnoreCase(method.getName()) || (returnType != null && returnType != void.class)) {
                builder.append("\t\treturn object.").append(utils.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
            } else {
                builder.append("\t\tobject.").append(utils.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
            }
            builder.append("\t}\r\n");
        }
    }
}
