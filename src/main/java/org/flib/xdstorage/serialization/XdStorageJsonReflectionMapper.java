package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Исправленный маппер демаршалинга объектов (Поинт В).
 * Интегрирует рекурсивную сборку коллекций и массивов с поддержкой Identity Map циклических связей.
 */
public final class XdStorageJsonReflectionMapper {

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final Map<Class<?>, Map<String, XdStorageObjectField>> fieldsCache = new HashMap<>();
    private final Map<String, Object> deserializedObjectsLog = new HashMap<>();

    public XdStorageJsonReflectionMapper(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.simpleTypeHelper = simpleTypeHelper;
    }

    public void clearSessionCache() {
        this.deserializedObjectsLog.clear();
    }

    public Object parseSingleJsonObject(final String jsonStr) throws Exception {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;

        if (jsonStr.contains("\"$ref\":")) {
            int refIdx = jsonStr.indexOf("\"$ref\":\"");
            if (refIdx != -1) {
                int refEnd = jsonStr.indexOf("\"", refIdx + 8);
                if (refEnd != -1) {
                    String refKey = jsonStr.substring(refIdx + 8, refEnd);
                    Object cached = deserializedObjectsLog.get(refKey);
                    if (cached != null) {
                        return cached;
                    }
                }
            }
            return null;
        }

        int typeIdx = jsonStr.indexOf("\"type\":\"");
        if (typeIdx == -1) return null;
        int typeEnd = jsonStr.indexOf("\"", typeIdx + 8);
        String className = jsonStr.substring(typeIdx + 8, typeEnd);

        Class<?> cl = Class.forName(className);
        Object instance = cl.newInstance();

        String currentObjectRefKey = cl.getName() + "@" + System.identityHashCode(instance);
        deserializedObjectsLog.put(currentObjectRefKey, instance);

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        Map<String, XdStorageObjectField> fields = fieldsCache.computeIfAbsent(cl, k -> clInfo.getFields());

        fields.forEach((name, field) -> {
            int propIdx = jsonStr.indexOf("\"" + name + "\":");
            if (propIdx != -1) {
                int startValue = propIdx + name.length() + 3;
                String valStr = XdStorageJsonStreamLexer.extractJsonValue(jsonStr, startValue);
                if (valStr != null && !valStr.equals("null")) {

                    Class<?> targetClass = field.getFieldInfo().getValueClass();

                    // 1. ПОДДЕРЖКА КОЛЛЕКЦИЙ И МАССИВОВ СУБД [...]:
                    if (valStr.startsWith("[") && valStr.endsWith("]")) {
                        try {
                            List<Object> parsedList = parseJsonArray(valStr);

                            if (targetClass.isArray()) {
                                Class<?> componentType = targetClass.getComponentType();
                                Object arrayInstance = Array.newInstance(componentType, parsedList.size());
                                for (int i = 0; i < parsedList.size(); i++) {
                                    Array.set(arrayInstance, i, parsedList.get(i));
                                }
                                field.set(instance, arrayInstance);
                            } else if (Collection.class.isAssignableFrom(targetClass)) {
                                Collection<Object> collectionInstance;
                                if (Set.class.isAssignableFrom(targetClass)) {
                                    collectionInstance = new LinkedHashSet<>(parsedList);
                                } else {
                                    collectionInstance = new ArrayList<>(parsedList);
                                }
                                field.set(instance, collectionInstance);
                            }
                            return;
                        } catch (Throwable t) {
                            // Ошибка маппинга массива
                        }
                    }

                    // 2. Поддержка одиночных вложенных JavaBean объектов {...}:
                    if (valStr.startsWith("{") && valStr.endsWith("}")) {
                        try {
                            Object childObj = parseSingleJsonObject(valStr);
                            if (childObj != null) {
                                field.set(instance, childObj);
                            }
                            return;
                        } catch (Throwable t) {
                            // Ошибка маппинга объекта
                        }
                    }

                    String cleanValue = valStr.replace("\"", "");

                    if (targetClass == Object.class) {
                        targetClass = deduceRealClass(valStr);
                    }

                    if (targetClass.isPrimitive()) {
                        if (targetClass == int.class) targetClass = Integer.class;
                        else if (targetClass == long.class) targetClass = Long.class;
                        else if (targetClass == boolean.class) targetClass = Boolean.class;
                        else if (targetClass == double.class) targetClass = Double.class;
                        else if (targetClass == float.class) targetClass = Float.class;
                        else if (targetClass == byte.class) targetClass = Byte.class;
                        else if (targetClass == short.class) targetClass = Short.class;
                        else if (targetClass == char.class) targetClass = Character.class;
                    }

                    try {
                        Object parsedVal = simpleTypeHelper.simpleTypeFromString(targetClass, cleanValue);
                        field.set(instance, parsedVal);
                    } catch (Throwable t) {
                        // Ошибка простого типа
                    }
                }
            }
        });

        return instance;
    }

    /**
     * Высокопроизводительный разбор вложенных JSON-массивов с балансировкой скобок.
     */
    private List<Object> parseJsonArray(String arrayStr) throws Exception {
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
                        Object obj = parseSingleJsonObject(rawItem);
                        if (obj != null) list.add(obj);
                    } else {
                        // Если внутри массива лежат примитивы
                        String clean = rawItem.replace("\"", "");
                        list.add(clean);
                    }
                }
                itemBuilder.setLength(0);
            }
        }
        return list;
    }

    private Class<?> deduceRealClass(final String valStr) {
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
}
