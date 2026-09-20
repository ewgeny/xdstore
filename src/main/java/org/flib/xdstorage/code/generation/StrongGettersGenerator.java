package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия генерации строго типизированных геттеров (Strong Getters) для сложных объектов СУБД.
 * Автоматически обрабатывает связи с родительскими сущностями для исключения циклических ссылок.
 */
public class StrongGettersGenerator implements XdStorageMethodGroupGenerator {

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        for (Method getter : ctx.strongGetters) {
            final int modifiers = getter.getModifiers();
            final TypeVariable<Method>[] typeParameters = getter.getTypeParameters();
            final Class<?> returnType = getter.getReturnType();
            final Type genReturnType = getter.getGenericReturnType();
            final String name = getter.getName();

            builder.append("\r\n\t@Override\r\n\t");
            builder.append(utils.buildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType, getter.getParameterTypes(), getter.getGenericParameterTypes(), getter.getExceptionTypes())).append(" {\r\n");

            // Если этот геттер возвращает родительский объект (помечен @XdStorageParentObject)
            if (getter == ctx.parentGetter) {
                builder.append("\t\tif(parent != null) {\r\n");
                builder.append("\t\t\treturn (");
                utils.buildCastType(builder, genReturnType, returnType);
                builder.append(")parent;\r\n");
                builder.append("\t\t} else {\r\n");
                builder.append("\t\t\treturn object.").append(utils.buildMethodCalling(name, getter.getParameterTypes())).append(";\r\n");
                builder.append("\t\t}\r\n");
            } else {
                // Обычный геттер сложного объекта — просто проксируем вызов оригинальной сущности
                builder.append("\t\treturn object.").append(utils.buildMethodCalling(name, getter.getParameterTypes())).append(";\r\n");
            }
            builder.append("\t}\r\n");
        }
    }
}
