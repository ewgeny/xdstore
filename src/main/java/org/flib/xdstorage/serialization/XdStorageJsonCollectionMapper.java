package org.flib.xdstorage.serialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Выделенный конвейер разбора вложенных JSON-массивов с балансировкой скобок (Поинт В).
 */
public final class XdStorageJsonCollectionMapper {

    private XdStorageJsonCollectionMapper() {
    }

    public static List<Object> parseJsonArray(final String arrayStr, final XdStorageJsonReflectionMapper mapper) throws Exception {
        List<Object> list = new ArrayList<>();
        String content = arrayStr.substring(1, arrayStr.length() - 1);
        if (content.trim().isEmpty()) return list;

        int bracesCount = 0;
        StringBuilder itemBuilder = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            }
            if (!inQuotes) {
                if (ch == '{') bracesCount++;
                if (ch == '}') bracesCount--;
            }

            if (bracesCount > 0 || inQuotes || (ch != ',' && ch != ' ')) {
                itemBuilder.append(ch);
            }

            if (bracesCount == 0 && !inQuotes && (ch == ',' || i == content.length() - 1)) {
                String rawItem = itemBuilder.toString().trim();
                if (!rawItem.isEmpty() && !rawItem.equals("null")) {
                    if (rawItem.startsWith("{") && rawItem.endsWith("}")) {
                        Object obj = mapper.parseSingleJsonObject(rawItem);
                        if (obj != null) list.add(obj);
                    } else {
                        String clean = rawItem.replace("\"", "");
                        list.add(clean);
                    }
                }
                itemBuilder.setLength(0);
            }
        }
        return list;
    }
}
