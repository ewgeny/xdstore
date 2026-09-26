package org.flib.xdstorage.serialization;

import java.io.IOException;
import java.io.Writer;

/**
 * Низкоуровневый высокопроизводительный Pretty-принтер JSON-символов (Поинт В).
 * Полностью избавлен от аллокаций в куче за счет статического кэша отступов.
 */
public final class XdStorageJsonPrinter {

    private static final String[] INDENTS = new String[64];
    static {
        INDENTS[0] = "";
        String singleIndent = "  ";
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < INDENTS.length; i++) {
            sb.append(singleIndent);
            INDENTS[i] = sb.toString();
        }
    }

    private XdStorageJsonPrinter() {
        // Утилитарный класс
    }

    public static void writeIndent(final Writer writer, int level) throws IOException {
        if (level < 0) level = 0;
        if (level >= INDENTS.length) {
            level = INDENTS.length - 1;
        }
        writer.write(INDENTS[level]);
    }

    public static String escapeJson(final String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
