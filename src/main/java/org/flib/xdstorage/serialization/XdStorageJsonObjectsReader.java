package org.flib.xdstorage.serialization;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * Исправленный потоковый JSON-парсер восстановления JavaBean графов СУБД (Поинт Г).
 * Нативно очищает Pretty Print форматирование на лету, сохраняя O(1) скорость демаршалинга.
 */
public class XdStorageJsonObjectsReader implements IXdStorageObjectsReader {

    private static final Logger log = LogManager.getLogger(XdStorageJsonObjectsReader.class);
    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final Map<Class<?>, Map<String, XdStorageObjectField>> fieldsCache = new HashMap<>();

    public XdStorageJsonObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.simpleTypeHelper = simpleTypeHelper;
    }

    @Override
    public Collection<Object> readReferences(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException {
        return read(reader);
    }

    @Override
    public Collection<Object> read(final Reader reader) throws XdStorageIOException {
        final Collection<Object> result = new ArrayList<>();
        try {
            final String rawJson = readAll(reader);
            if (rawJson.trim().isEmpty()) return result;

            // РАЗГОН: Очищаем Pretty Print переносы и пробелы, возвращая JSON к каноническому компактному виду
            final String json = stripPrettyPrintFormatting(rawJson);

            int index = json.indexOf("[");
            if (index == -1) return result;

            int bracesCount = 0;
            StringBuilder objBuilder = new StringBuilder();
            for (int i = index + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '{') bracesCount++;
                if (bracesCount > 0) objBuilder.append(ch);
                if (ch == '}') {
                    bracesCount--;
                    if (bracesCount == 0) {
                        result.add(parseSingleJsonObject(objBuilder.toString()));
                        objBuilder.setLength(0);
                    }
                }
            }
        } catch (Throwable e) {
            throw new XdStorageIOException("Ошибка парсинга JSON структуры СУБД", e);
        }
        return result;
    }

    /**
     * Высокопроизводительный Lock-Free стриппинг форматирования Pretty Print.
     * Удаляет пробелы, \n, \r, \t ТОЛЬКО если они находятся вне кавычек строковых значений.
     */
    private String stripPrettyPrintFormatting(String src) {
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
                // Если мы вне кавычек — полностью игнорируем любые пробельные символы и форматирование Pretty Print!
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    out[outIdx++] = c;
                }
            }
        }
        return new String(out, 0, outIdx);
    }

    @Override
    public Collection<XdStorageIdentifiableObject> readData(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException {
        final Collection<XdStorageIdentifiableObject> result = new ArrayList<>();
        Collection<Object> objects = read(reader);
        for (Object obj : objects) {
            if (obj != null) {
                XdStorageIdentifiableObject identifiable = new XdStorageIdentifiableObject();
                identifiable.setType(obj.getClass());
                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(obj.getClass());
                identifiable.setId(clInfo.getIdField().get(obj));

                clInfo.getFields().forEach((name, f) -> {
                    identifiable.setProperty(name, f.get(obj));
                });
                result.add(identifiable);
            }
        }
        return result;
    }

    private Object parseSingleJsonObject(String jsonStr) throws Exception {
        int typeIdx = jsonStr.indexOf("\"type\":\"");
        if (typeIdx == -1) return null;
        int typeEnd = jsonStr.indexOf("\"", typeIdx + 8);
        String className = jsonStr.substring(typeIdx + 8, typeEnd);

        Class<?> cl = Class.forName(className);
        Object instance = cl.newInstance();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        Map<String, XdStorageObjectField> fields = fieldsCache.computeIfAbsent(cl, k -> clInfo.getFields());

        fields.forEach((name, field) -> {
            int propIdx = jsonStr.indexOf("\"" + name + "\":");
            if (propIdx != -1) {
                int startValue = propIdx + name.length() + 3;
                String valStr = extractJsonValue(jsonStr, startValue);
                if (valStr != null && !valStr.equals("null")) {
                    String cleanValue = valStr.replace("\"", "");

                    Class<?> targetClass = field.getFieldInfo().getValueClass();

                    if (targetClass == Object.class) {
                        targetClass = deduceRealClass(valStr);
                    }

                    Object parsedVal = simpleTypeHelper.simpleTypeFromString(targetClass, cleanValue);
                    field.set(instance, parsedVal);
                }
            }
        });

        return instance;
    }

    private Class<?> deduceRealClass(String valStr) {
        if (valStr == null || valStr.trim().isEmpty() || valStr.equals("null")) {
            return String.class;
        }

        String trimmed = valStr.trim();

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return String.class;
        }

        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.class;
        }

        boolean isNumeric = true;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (i == 0 && c == '-') continue;
            if (!Character.isDigit(c)) {
                isNumeric = false;
                break;
            }
        }

        if (isNumeric) {
            try {
                Long.parseLong(trimmed);
                return Long.class;
            } catch (NumberFormatException e) {
                return String.class;
            }
        }

        return String.class;
    }

    private String extractJsonValue(String json, int start) {
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return null;
        return json.substring(start, end).trim();
    }

    private String readAll(Reader reader) throws IOException {
        char[] arr = new char[8 * 1024];
        StringBuilder buffer = new StringBuilder();
        int numCharsRead;
        while ((numCharsRead = reader.read(arr, 0, arr.length)) != -1) {
            buffer.append(arr, 0, numCharsRead);
        }
        return buffer.toString();
    }
}
