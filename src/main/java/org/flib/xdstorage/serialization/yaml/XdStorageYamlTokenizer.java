package org.flib.xdstorage.serialization.yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * Высокопроизводительный построчный lock-free токенизатор YAML на табуляциях.
 * Оперирует буфером заглядывания вперёд peekToken для безопасного рекурсивного спуска.
 */
public class XdStorageYamlTokenizer {

    private final BufferedReader reader;

    // Внутренний буфер для реализации заглядывания вперёд (Look-Ahead Barrier)
    private YamlLineToken peekedToken = null;

    public XdStorageYamlTokenizer(final Reader reader) {
        this.reader = (reader instanceof BufferedReader) ?
                (BufferedReader) reader : new BufferedReader(reader);
    }

    /**
     * Позволяет высокоуровневому демаршаллеру заглянуть на один токен вперёд,
     * не извлекая его физически из конвейера чтения. Ключевой элемент рекурсивного спуска!
     */
    public YamlLineToken peekToken() throws IOException {
        if (peekedToken == null) {
            peekedToken = readNextTokenFromServerStream();
        }
        return peekedToken;
    }

    /**
     * Потребляет и возвращает следующий валидный токен из YAML-файла.
     * Если перед этим вызывался peekToken(), бесшовно отдаёт объект из буфера.
     */
    public YamlLineToken nextToken() throws IOException {
        if (peekedToken != null) {
            YamlLineToken res = peekedToken;
            peekedToken = null; // Очищаем буфер заглядывания
            return res;
        }
        return readNextTokenFromServerStream();
    }

    /**
     * Внутренний прямолинейный алгоритм посимвольного подсчёта табуляций и сплита строк.
     */
    private YamlLineToken readNextTokenFromServerStream() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            // 1. Мгновенно отсекаем пустые строки и комментарии СУБД
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }

            // 2. МOЛНИЕНОСНЫЙ ПОДСЧЁТ ВЛОЖЕННОСТИ:
            // Считаем количество префиксных '\t' в начале физической строки
            int level = 0;
            while (level < line.length() && line.charAt(level) == '\t') {
                level++;
            }

            // 3. Срезаем отступы за один проход
            String cleanLine = line.substring(level).trim();

            // 4. Детектируем и вырезаем маркер элемента коллекции "- "
            boolean isListItem = false;
            if (cleanLine.startsWith("- ")) {
                isListItem = true;
                cleanLine = cleanLine.substring(2).trim();
            }

            // 5. Разрезаем чистую строку по первому символу двоеточия ':'
            int colonIdx = cleanLine.indexOf(':');
            String key = cleanLine;
            String value = null;

            if (colonIdx != -1) {
                key = cleanLine.substring(0, colonIdx).trim();
                String rawValue = cleanLine.substring(colonIdx + 1).trim();
                if (!rawValue.isEmpty()) {
                    value = parseRawValue(rawValue);
                }
            } else {
                // Если двоеточия нет, это может быть плоский скалярный элемент коллекции (например, "- null")
                if (isListItem) {
                    value = parseRawValue(cleanLine);
                    key = value == null ? cleanLine : null;
                }
            }

            return new YamlLineToken(level, key, value, isListItem);
        }
        return null; // Конец файла (EOF)
    }

    /**
     * По канону нашего соглашения: распаковывает одинарные кавычки '' -> '
     * и распознает явные маркеры пустых значений.
     */
    private String parseRawValue(final String rawValue) {
        if ("null".equals(rawValue) || "~".equals(rawValue)) {
            return null;
        }
        // Распаковка одинарных кавычек по твоему алгоритму без прикрас
        if (rawValue.startsWith("'") && rawValue.endsWith("'") && rawValue.length() >= 2) {
            String content = rawValue.substring(1, rawValue.length() - 1);
            return content.replace("''", "'");
        }
        return rawValue;
    }
}
