package org.flib.xdstorage.code.generation;

import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Адаптер, позволяющий unmodifiable-генератору повторно использовать
 * стратегию сборки геттеров простых типов и примитивов.
 */
public class SimpleGettersGeneratorAdapter implements XdStorageUnmodifiableMethodGenerator {

    // Делегат базовой стратегии
    private final SimpleGettersGenerator delegate = new SimpleGettersGenerator();

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        // Передаем управление общему кодогенератору простых типов
        delegate.generate(builder, clazz, ctx, utils);
    }
}
