package org.flib.xdstorage.serialization;

import java.io.IOException;
import java.io.Reader;

/**
 * Низкоуровневый лексер-токенизатор JSON-потока СУБД (Поинт В).
 * Инкапсулирует очистку Pretty Print и детерминированную вырезку токенов с балансировкой скобок.
 */
public final class XdStorageJsonStreamLexer {

    private XdStorageJsonStreamLexer() {
        // Константный утилитарный класс
    }

    public static String readAll(final Reader reader) throws IOException {
        char[] arr = new char[8 * 1024];
        StringBuilder buffer = new StringBuilder();
        int numCharsRead;
        while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
            buffer.append(arr, 0, numCharsRead);
        }
        return buffer.toString();
    }

    public static String stripPrettyPrintFormatting(final String src) {
        if (src == null || src.isEmpty()) return "";
        char[] in = src.toCharArray();
        char[] out = new char[in.length];
        int outIdx = 0;
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < in.length; i++) {
            char c = in[i];
            if (escaped) {
                out[outIdx++] = c;
                escaped = false;
                continue;
            }
            if (c == '\\') {
                out[outIdx++] = c;
                escaped = true;
                continue;
            }
            if (c == '"') {
                inQuotes = !inQuotes;
                out[outIdx++] = c;
                continue;
            }

            if (inQuotes) {
                out[outIdx++] = c;
            } else {
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    out[outIdx++] = c;
                }
            }
        }
        return new String(out, 0, outIdx);
    }

    public static String extractJsonValue(final String json, final int start) {
        if (start >= json.length()) return null;

        char firstChar = json.charAt(start);

        if (firstChar == '{' || firstChar == '[') {
            char openBrace = firstChar;
            char closeBrace = (firstChar == '{') ? '}' : ']';
            int balance = 0;
            boolean inQuotes = false;

            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '"') {
                    inQuotes = !inQuotes;
                }
                if (!inQuotes) {
                    if (c == openBrace) balance++;
                    if (c == closeBrace) balance--;
                    if (balance == 0) {
                        return json.substring(start, i + 1);
                    }
                }
            }
        }

        int end = json.indexOf(",", start);
        int braceEnd = json.indexOf("}", start);
        int arrayEnd = json.indexOf("]", start);

        int finalEnd = end;
        if (finalEnd == -1 || (braceEnd != -1 && braceEnd < finalEnd)) finalEnd = braceEnd;
        if (finalEnd == -1 || (arrayEnd != -1 && arrayEnd < finalEnd)) finalEnd = arrayEnd;

        if (finalEnd == -1) return null;

        String result = json.substring(start, finalEnd).trim();
        if ("\"null\"".equals(result)) {
            return "null";
        }
        return result;
    }
}
