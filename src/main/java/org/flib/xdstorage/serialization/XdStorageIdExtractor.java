package org.flib.xdstorage.serialization;

import org.flib.xdstorage.utils.XdStorageClassInfo;

/**
 * Исправленный высокопроизводительный экстрактор идентификаторов СУБД (Поинт В).
 * Реализует строгий конечный автомат разбора токенов ключей на первом уровне вложенности JSON.
 */
public final class XdStorageIdExtractor {

    private static final String[] COMMON_ID_TOKENS = {"id", "idgeneration", "indexName", "resourceId"};

    private XdStorageIdExtractor() {
    }

    public static String extractId(final String jsonStr, final XdStorageClassInfo clInfo) {
        if (jsonStr == null || jsonStr.isEmpty()) return null;

        String targetIdName = (clInfo != null && clInfo.getIdField() != null) ? clInfo.getIdField().getName() : null;

        char[] chars = jsonStr.toCharArray();
        int bracesCount = 0;
        int bracketsCount = 0;
        boolean inQuotes = false;
        boolean escaped = false;

        // Флаг, подсвечивающий, что текущие кавычки описывают именно КЛЮЧ свойства, а не его значение
        boolean parsingKey = false;
        StringBuilder currentKeyBuilder = new StringBuilder();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                if (inQuotes) {
                    // Переключаем автомат: если мы на первом уровне и перед этим не было двоеточия,
                    // значит, открылись кавычки нового КЛЮЧА свойства
                    if (bracesCount == 1 && bracketsCount == 0 && !parsingKey) {
                        currentKeyBuilder.setLength(0); // Сбрасываем старый мусор
                        parsingKey = true;
                    }
                } else {
                    // Кавычки закрылись
                    if (parsingKey) {
                        parsingKey = false;
                        String extractedKey = currentKeyBuilder.toString().trim();

                        // Проверяем совпадение ключа ID на корневом уровне
                        if ((targetIdName != null && targetIdName.equals(extractedKey)) || isCommonToken(extractedKey)) {
                            // Нашли точную позицию ключа ID! Извлекаем его значение
                            int colonIdx = jsonStr.indexOf(":", i);
                            if (colonIdx != -1) {
                                String valStr = XdStorageJsonStreamLexer.extractJsonValue(jsonStr, colonIdx + 1);
                                if (valStr != null && !valStr.equals("null") && !valStr.startsWith("{") && !valStr.startsWith("[")) {
                                    return valStr.replace("\"", "").trim();
                                }
                            }
                        }
                    }
                }
                continue;
            }

            if (inQuotes) {
                // Накапливаем символы в буфер ТОЛЬКО если автомат находится в фазе разбора ключа
                if (parsingKey) {
                    currentKeyBuilder.append(c);
                }
            } else {
                if (c == '{') bracesCount++;
                if (c == '}') bracesCount--;
                if (c == '[') bracketsCount++;
                if (c == ']') bracketsCount--;

                // Если встретили запятую или двоеточие вне кавычек — сбрасываем состояние парсинга ключа
                if (c == ',' || c == ':') {
                    parsingKey = false;
                }
            }
        }

        // Железобетонный плоский фолбэк для простых строк без вложенностей
        if (targetIdName != null) {
            int idPropIdx = jsonStr.indexOf("\"" + targetIdName + "\":");
            if (idPropIdx != -1) {
                int startIdVal = idPropIdx + targetIdName.length() + 3;
                String idValStr = XdStorageJsonStreamLexer.extractJsonValue(jsonStr, startIdVal);
                if (idValStr != null && !idValStr.equals("null") && !idValStr.startsWith("{") && !idValStr.startsWith("[")) {
                    return idValStr.replace("\"", "").trim();
                }
            }
        }

        return null;
    }

    private static boolean isCommonToken(final String key) {
        for (String token : COMMON_ID_TOKENS) {
            if (token.equals(key)) return true;
        }
        return false;
    }
}
