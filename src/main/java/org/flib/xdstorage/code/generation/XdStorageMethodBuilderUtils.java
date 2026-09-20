package org.flib.xdstorage.code.generation;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * Интерфейс-утилита для конструирования элементов исходного кода Java.
 * Передается в стратегии генерации методов, изолируя их от прямого
 * вызова низкоуровневых рефлексивных методов базового класса.
 */
public interface XdStorageMethodBuilderUtils {

    /**
     * Формирует полноценное строковое определение метода (сигнатуру):
     * модификаторы, генерик-параметры, тип возвращаемого значения, имя, аргументы и блоки throws.
     */
    String callBuildMethodDefinition(int modifiers, String name, TypeVariable<Method>[] typeParameters,
                                     Class<?> returnType, Type genReturnType, Class<?>[] parameterTypes,
                                     Type[] genParameterTypes, Class<?>[] exceptionTypes);

    /**
     * Формирует строковый вызов оригинального метода с автоматической подстановкой сгенерированных переменных var0, var1 и т.д.
     */
    String callBuildMethodCalling(String name, Class<?>[] parameterTypes);

    /**
     * Вспомогательный метод для построения строкового представления приведения типов (Casting).
     * Корректно обрабатывает параметризованные типы (Generics), массивы и стандартные классы Java.
     */
    default void buildCastType(final StringBuilder builder, final Type genReturnType, final Class<?> returnType) {
        if (genReturnType != null && (genReturnType instanceof ParameterizedType || genReturnType instanceof TypeVariable)) {
            builder.append(genReturnType.toString());
        } else if (returnType.isArray()) {
            builder.append(returnType.getComponentType().getName()).append("[]");
        } else {
            builder.append(returnType.getName());
        }
    }

    /**
     * Адаптер-переходник для вызова стандартного сборщика сигнатур из базового генератора.
     */
    default String buildMethodDefinition(int modifiers, String name, TypeVariable<Method>[] typeParameters,
                                         Class<?> returnType, Type genReturnType, Class<?>[] parameterTypes,
                                         Type[] genParameterTypes, Class<?>[] exceptionTypes) {
        return callBuildMethodDefinition(modifiers, name, typeParameters, returnType, genReturnType,
                parameterTypes, genParameterTypes, exceptionTypes);
    }

    /**
     * Адаптер-переходник для вызова стандартного сборщика вызовов из базового генератора.
     */
    default String buildMethodCalling(String name, Class<?>[] parameterTypes) {
        return callBuildMethodCalling(name, parameterTypes);
    }
}
