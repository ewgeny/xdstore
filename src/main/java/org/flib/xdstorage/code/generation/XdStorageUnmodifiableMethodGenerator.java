package org.flib.xdstorage.code.generation;

import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия для генерации определенной группы unmodifiable-методов в байт-коде прокси.
 */
public interface XdStorageUnmodifiableMethodGenerator {
    void generate(StringBuilder builder, Class<?> clazz, GenerationContext ctx, XdStorageMethodBuilderUtils utils);
}
