package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;

public class UnmodifiableBlockMutationsGenerator implements XdStorageUnmodifiableMethodGenerator {

    @Override
    public void generate(StringBuilder builder, Class<?> cl, GenerationContext ctx, XdStorageMethodBuilderUtils utils) {
        // Блокируем стандартные сеттеры
        for (Method method : ctx.setters) {
            generateBlockBody(builder, method, utils);
        }
        // Блокируем остальные методы модификации логики
        for (Method method : ctx.toCloseMethods) {
            generateBlockBody(builder, method, utils);
        }
    }

    private void generateBlockBody(StringBuilder builder, Method method, XdStorageMethodBuilderUtils utils) {
        builder.append("\r\n\t@Override\r\n\t");
        builder.append(utils.buildMethodDefinition(method.getModifiers(), method.getName(), method.getTypeParameters(), method.getReturnType(), method.getGenericReturnType(), method.getParameterTypes(), method.getGenericParameterTypes(), method.getExceptionTypes())).append(" {\r\n");
        builder.append("\t\ttransaction.markRollbackOnly();\r\n");
        builder.append("\t\tthrow new ").append(XdStorageRuntimeException.class.getName());
        builder.append("(\"modification of viewable object is not allowed\");\r\n");
        builder.append("\t}\r\n");
    }
}
