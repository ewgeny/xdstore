package org.flib.xdstorage.serialization.yaml;

import java.io.IOException;
import java.io.Writer;

public class XdStorageYamlEmitter {
    private final Writer writer;
    private int currentLevel = 0;
    private boolean isNewLine = true;

    public XdStorageYamlEmitter(Writer writer) {
        this.writer = writer;
    }

    /**
     * Пишет системный перенос строки \r\n и выставляет флаг ожидания отступов.
     */
    public void newLine() throws IOException {
        writer.append("\r\n");
        isNewLine = true;
    }

    /**
     * Внутренний fail-safe метод. Если взведен флаг новой строки,
     * нарезает ровно currentLevel символов '\t' и сбрасывает флаг.
     */
    private void indent() throws IOException {
        if (isNewLine) {
            for (int i = 0; i < currentLevel; i++) {
                writer.append('\t');
            }
            isNewLine = false;
        }
    }

    /**
     * Печатает имя свойства (ключ) с учетом текущего отступа яруса.
     */
    public void writeKey(String key) throws IOException {
        indent();
        writer.append(key).append(": ");
    }

    /**
     * Печатает атомарное значение (число, строку в кавычках) и сразу завершает строку.
     */
    public void writeScalar(String value) throws IOException {
        indent();
        writer.append(value);
        newLine();
    }

    /**
     * Открывает новый структурный блок (object:, collection: и т.д.),
     * печатает его имя и увеличивает внутренний уровень табуляции на 1.
     */
    public void openBlock(String blockName) throws IOException {
        indent();
        writer.append(blockName).append(":");
        newLine();
        currentLevel++;
    }

    /**
     * Закрывает структурный блок, fail-safe уменьшая уровень табуляции на 1.
     */
    public void closeBlock() {
        if (currentLevel > 0) {
            currentLevel--;
        }
    }
}
