package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

public class SimpleGettersGenerator implements XdStorageMethodGroupGenerator {
    @Override
    public void generate(StringBuilder builder, Class<?> cl, GenerationContext ctx, XdStorageMethodBuilderUtils utils) {
        for (Method getter : ctx.fieldsGetters) {
            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(getter.getModifiers(), getter.getName(), getter.getTypeParameters(),
                    getter.getReturnType(), getter.getGenericReturnType(), getter.getParameterTypes(),
                    getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");
            builder.append("\t\treturn object.").append(utils.buildMethodCalling(getter.getName(), getter.getParameterTypes())).append(";\r\n");
            builder.append("\t}\r\n");
        }
    }
}
