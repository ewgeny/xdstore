package org.flib.xdstorage.code.generation;

import java.io.Writer;
import java.lang.reflect.Method;
import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия для генерации определенной группы методов в байт-коде прокси.
 */
public interface XdStorageMethodGroupGenerator {
    void generate(StringBuilder builder, Class<?> clazz, GenerationContext ctx, XdStorageMethodBuilderUtils utils);
}
