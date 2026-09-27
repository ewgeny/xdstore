package org.flib.xdstorage.serialization;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Декомпозированный маппер демаршалинга объектов СУБД (Поинт В).
 * Детерминированно разрешает циклические графы, изолируя чистые $ref ссылки от вложенных свойств.
 */
public final class XdStorageJsonReflectionMapper {

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final Map<Class<?>, Map<String, XdStorageObjectField>> fieldsCache = new HashMap<>();

    // Изолированный контекст кэша сессии для каждого потока воркера СУБД
    private final ThreadLocal<Map<String, Object>> sessionObjectsCache = ThreadLocal.withInitial(HashMap::new);

    public XdStorageJsonReflectionMapper(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.simpleTypeHelper = simpleTypeHelper;
    }

    public void clearSessionCache() {
        this.sessionObjectsCache.get().clear();
    }

    public Object parseSingleJsonObject(final String jsonStr) throws Exception {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return null;

        Map<String, Object> cache = sessionObjectsCache.get();

        // ИСПРАВЛЕНИЕ БАРЬЕРА ССЫЛОК: Перехватываем маркер $ref ТОЛЬКО если строка является
        // чистой заглушкой ссылки (не содержит объявления нового мета-типа "type" на корневом уровне)!
        if (jsonStr.contains("\"$ref\":") && !jsonStr.contains("\"type\":")) {
            int refIdx = jsonStr.indexOf("\"$ref\":\"");
            if (refIdx != -1) {
                int refEnd = jsonStr.indexOf("\"", refIdx + 8);
                if (refEnd != -1) {
                    String refFullKey = jsonStr.substring(refIdx + 8, refEnd);

                    String cleanRefId = refFullKey;
                    int atIdx = refFullKey.lastIndexOf('@');
                    if (atIdx != -1) {
                        cleanRefId = refFullKey.substring(atIdx + 1);
                    }

                    Object cached = cache.get(cleanRefId);
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
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        Map<String, XdStorageObjectField> fields = fieldsCache.computeIfAbsent(cl, k -> clInfo.getFields());

        // Извлекаем детерминированный бизнес-идентификатор через экстрактор
        String cleanId = XdStorageIdExtractor.extractId(jsonStr, clInfo);

        // Используем чистый ID в качестве ключа дедупликации в памяти потока
        String objectCacheKey = (cleanId != null) ? cleanId : ("temp_" + System.identityHashCode(jsonStr));

        if (cache.containsKey(objectCacheKey)) {
            return cache.get(objectCacheKey);
        }

        final Object instance = cl.newInstance();
        cache.put(objectCacheKey, instance);

        fields.forEach((name, field) -> {
            int propIdx = jsonStr.indexOf("\"" + name + "\":");
            if (propIdx != -1) {
                int startValue = propIdx + name.length() + 3;
                String valStr = XdStorageJsonStreamLexer.extractJsonValue(jsonStr, startValue);
                if (valStr != null && !valStr.equals("null")) {

                    Class<?> fieldBaseClass = field.getFieldInfo().getClazz();

                    // Сборка массивов и коллекций передается XdStorageJsonCollectionMapper
                    if (valStr.startsWith("[") && valStr.endsWith("]")) {
                        try {
                            List<Object> parsedList = XdStorageJsonCollectionMapper.parseJsonArray(valStr, this);
                            if (fieldBaseClass.isArray()) {
                                Class<?> componentType = fieldBaseClass.getComponentType();
                                Object arrayInstance = Array.newInstance(componentType, parsedList.size());
                                for (int i = 0; i < parsedList.size(); i++) {
                                    Array.set(arrayInstance, i, parsedList.get(i));
                                }
                                field.set(instance, arrayInstance);
                            } else if (Collection.class.isAssignableFrom(fieldBaseClass)) {
                                Collection<Object> collectionInstance;
                                if (Set.class.isAssignableFrom(fieldBaseClass)) {
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
                    Class<?> targetClass = field.getFieldInfo().getValueClass();

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
