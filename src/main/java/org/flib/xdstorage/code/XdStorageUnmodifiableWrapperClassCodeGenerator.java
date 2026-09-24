package org.flib.xdstorage.code;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import java.util.Map;

/**
 * Полностью декомпозированный сервис генерации кода немодифицируемых оберток (Поинт В).
 * Делегирует строковую сборку в XdStorageUnmodifiableBlockGettersGenerator и XdStorageUnmodifiableBlockSettersGenerator.
 */
public class XdStorageUnmodifiableWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    public XdStorageUnmodifiableWrapperClassCodeGenerator() {
        super(true);
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        final GenerationContext ctx = collectMethodsAndClasses(cl);

        final StringBuilder builder = new StringBuilder();
        final String className = buildClassName(classesPackage, cl);

        // Построение структуры класса-наследника
        appendPackageAndClassHeader(builder, classesPackage, className, cl);
        appendPrivateFields(builder, cl);
        appendConstructor(builder, className, cl);
        appendDummyMarker(builder);

        // Делегирование генерации методов в выделенные изолированные суб-компоненты
        XdStorageUnmodifiableBlockGettersGenerator.generateSimpleGetters(builder, ctx, this);
        XdStorageUnmodifiableBlockGettersGenerator.generateStrongGetters(builder, ctx, this);
        XdStorageUnmodifiableBlockGettersGenerator.generateLoadByGetGetters(builder, ctx, this);
        XdStorageUnmodifiableBlockSettersGenerator.generateSettersMethods(builder, ctx, this);
        XdStorageUnmodifiableBlockSettersGenerator.generateClosedMethods(builder, ctx, this);
        generateObjectMethods(builder);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
        return code;
    }

    private void appendPackageAndClassHeader(final StringBuilder builder, final String classesPackage, final String className, final Class<?> cl) {
        builder.append("package ").append(classesPackage).append(";\r\n\r\n");
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageUnmodifiableWrapper.class.getName()).append(" {\r\n");
    }

    private void appendPrivateFields(final StringBuilder builder, final Class<?> cl) {
        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate ").append(Object.class.getName()).append(" parent;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorage.class.getName()).append(" storage;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorageTransaction.class.getName()).append(" transaction;\r\n");
    }

    private void appendConstructor(final StringBuilder builder, final String className, final Class<?> cl) {
        builder.append("\r\n\tpublic ").append(className).append("(");
        builder.append(Object.class.getName()).append(" parent, ");
        builder.append(cl.getName()).append(" object, ");
        builder.append(IXdStorage.class.getName()).append(" storage, ");
        builder.append(IXdStorageTransaction.class.getName()).append(" transaction");
        builder.append(") {\r\n");
        builder.append("\t\tthis.object = object;\r\n");
        builder.append("\t\tthis.parent = parent;\r\n");
        builder.append("\t\tthis.storage = storage;\r\n");
        builder.append("\t\tthis.transaction = transaction;\r\n");
        builder.append("\t}\r\n");
    }

    private void appendDummyMarker(final StringBuilder builder) {
        builder.append("\r\n\tpublic static boolean isDummyUnmodifiableWrapper() {\r\n\t\treturn false;\r\n\t}\r\n");
    }

    private void generateObjectMethods(final StringBuilder builder) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n\t\treturn object.hashCode();\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n\t\treturn object.equals(obj);\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n\t\treturn object.toString();\r\n\t}\r\n");
    }

    @Override
    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "UnmodifiableWrapper";
    }
}
