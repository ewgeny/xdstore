package org.flib.xdstorage.code.generation;

import org.flib.xdstorage.code.XdStorageAbstractClassCodeGenerator.GenerationContext;

/**
 * Стратегия генерации стандартных контрактов java.lang.Object (equals, hashCode, toString).
 * Обеспечивает прозрачное проксирование идентичности объекта на уровне JVM.
 */
public class StandardObjectMethodsGenerator implements XdStorageMethodGroupGenerator {

    @Override
    public void generate(final StringBuilder builder, final Class<?> clazz, final GenerationContext ctx, final XdStorageMethodBuilderUtils utils) {
        // Переопределение hashCode()
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n");
        builder.append("\t\treturn object.hashCode();\r\n");
        builder.append("\t}\r\n");

        // Переопределение equals()
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n");
        builder.append("\t\treturn object.equals(obj);\r\n");
        builder.append("\t}\r\n");

        // Переопределение toString()
        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n");
        builder.append("\t\treturn object.toString();\r\n");
        builder.append("\t}\r\n");
    }
}
