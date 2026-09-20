package org.flib.xdstorage.code.generation;

import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Адаптер, позволяющий unmodifiable-генератору повторно использовать
 * базовую стратегию генерации стандартных методов java.lang.Object.
 */
public class StandardObjectMethodsGeneratorAdapter implements XdStorageUnmodifiableMethodGenerator {

    // Делегат базовой стратегии
    private final StandardObjectMethodsGenerator delegate = new StandardObjectMethodsGenerator();

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        // Передаем управление общему кодогенератору объектных методов
        delegate.generate(builder, clazz, ctx, utils);
    }
}
