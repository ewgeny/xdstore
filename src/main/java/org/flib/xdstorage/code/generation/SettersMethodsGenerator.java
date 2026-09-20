package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия генерации открытых методов-модификаторов (сеттеров) для прокси-оберток СУБД.
 */
public class SettersMethodsGenerator implements XdStorageMethodGroupGenerator {

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        for (Method method : ctx.setters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(
                    method.getModifiers(),
                    method.getName(),
                    method.getTypeParameters(),
                    method.getReturnType(),
                    method.getGenericReturnType(),
                    method.getParameterTypes(),
                    method.getGenericParameterTypes(),
                    method.getExceptionTypes()
            )).append(" {\r\n");

            // Прозрачно перенаправляем вызов оригинальному объекту СУБД
            builder.append("\t\tobject.").append(utils.buildMethodCalling(method.getName(), method.getParameterTypes())).append(";\r\n");
            builder.append("\t}\r\n");
        }
    }
}
