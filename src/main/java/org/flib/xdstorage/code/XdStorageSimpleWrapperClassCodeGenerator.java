package org.flib.xdstorage.code;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Финальный декомпозированный сервис генерации кода прокси ленивой загрузки (Поинт В).
 * Избавлен от сотен строк строковой сборки, делегируя её в специализированные суб-генераторы.
 */
public class XdStorageSimpleWrapperClassCodeGenerator extends XdStorageAbstractClassCodeGenerator {

    public XdStorageSimpleWrapperClassCodeGenerator() {
        super(true);
    }

    @Override
    public Map<String, String> generate(final String classesPackage, final Class<?> cl, final Map<String, String> code) {
        final GenerationContext ctx = collectMethodsAndClasses(cl);

        final StringBuilder builder = new StringBuilder();
        final String className = buildClassName(classesPackage, cl);

        // Сборка каркаса исходного файла Java
        appendPackageAndClassHeader(builder, classesPackage, className, cl);
        appendPrivateFields(builder, cl);
        appendConstructor(builder, className, cl);
        appendDummyMarker(builder);

        // Делегирование генерации методов в выделенные изолированные классы-компоненты
        generateSimpleWrapperMethods(builder, cl);
        XdStorageWrapperBlockGettersGenerator.generateSimpleGetters(builder, ctx, this);
        XdStorageWrapperBlockGettersGenerator.generateStrongGetters(builder, ctx, this);
        XdStorageWrapperBlockGettersGenerator.generateLoadByGetGetters(builder, ctx, this);
        XdStorageWrapperBlockSettersGenerator.generateSettersMethods(builder, ctx, this);
        XdStorageWrapperBlockSettersGenerator.generateClosedMethods(builder, ctx, this);
        generateObjectMethods(builder);

        builder.append("}\r\n");

        code.put(classesPackage + "." + className, builder.toString());
        return code;
    }

    private void appendPackageAndClassHeader(final StringBuilder builder, final String classesPackage, final String className, final Class<?> cl) {
        builder.append("package ").append(classesPackage).append(";\r\n\r\n");
        builder.append("public class ").append(className).append(" extends ").append(cl.getName());
        builder.append(" implements ").append(IXdStorageSimpleWrapper.class.getName()).append(" {\r\n");
    }

    private void appendPrivateFields(final StringBuilder builder, final Class<?> cl) {
        builder.append("\r\n\tprivate ").append(cl.getName()).append(" object;\r\n");
        builder.append("\r\n\tprivate ").append(Object.class.getName()).append(" parent;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorage.class.getName()).append(" storage;\r\n");
        builder.append("\r\n\tprivate ").append(IXdStorageTransaction.class.getName()).append(" transaction;\r\n");
        builder.append("\r\n\tprivate volatile boolean reference__ = true;\r\n");
        builder.append("\r\n\tprivate ").append(Lock.class.getName()).append(" lock__ = new ").append(ReentrantLock.class.getName()).append("();\r\n");
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
        builder.append("\r\n\tpublic static boolean isDummySimpleWrapper() {\r\n\t\treturn false;\r\n\t}\r\n");
    }

    private void generateSimpleWrapperMethods(final StringBuilder builder, final Class<?> cl) {
        builder.append("\r\n\t@Override\r\n\tpublic Object getObjectId__() {");
        builder.append("\r\n\t\treturn object.").append(getObjectGetIdMethodName(cl)).append("();\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic void setReference__(boolean reference__) {");
        builder.append("\r\n\t\tthis.reference__ = reference__;\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic boolean isReference__() {");
        builder.append("\r\n\t\treturn this.reference__;\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic void lock__() {");
        builder.append("\r\n\t\tlock__.lock();\r\n\t}\r\n");

        builder.append("\r\n\t@Override\r\n\tpublic void unlock__() {");
        builder.append("\r\n\t\tlock__.unlock();\r\n\t}\r\n");
    }

    private String getObjectGetIdMethodName(final Class<?> cl) {
        final String idFieldName = getIdFieldName(cl);
        return "get" + Character.toUpperCase(idFieldName.charAt(0)) + idFieldName.substring(1);
    }

    private void generateObjectMethods(final StringBuilder builder) {
        builder.append("\r\n\t@Override\r\n\tpublic int hashCode() {\r\n\t\treturn object.hashCode();\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic boolean equals(Object obj) {\r\n\t\treturn object.equals(obj);\r\n\t}\r\n");
        builder.append("\r\n\t@Override\r\n\tpublic String toString() {\r\n\t\treturn object.toString();\r\n\t}\r\n");
    }

    protected String buildClassName(final String classesPackage, final Class<?> cl) {
        return cl.getSimpleName() + "SimpleWrapper";
    }
}
