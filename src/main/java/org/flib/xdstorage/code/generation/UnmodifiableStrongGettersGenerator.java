package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

public class UnmodifiableStrongGettersGenerator implements XdStorageUnmodifiableMethodGenerator {

    @Override
    public void generate(StringBuilder builder, Class<?> cl, GenerationContext ctx, XdStorageMethodBuilderUtils utils) {
        for (Method getter : ctx.strongGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");

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
