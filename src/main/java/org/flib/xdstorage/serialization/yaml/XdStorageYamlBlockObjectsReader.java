package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Изолированный рефлексивный парсер JavaBeans-сущностей и ORM-ссылок СУБД.
 */
public class XdStorageYamlBlockObjectsReader {

    private static final Map<Class<?>, Map<String, XdStorageObjectField>> propertiesCache = new HashMap<>();

    public static Object readObject(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        YamlLineToken token = tokenizer.peekToken();
        if (token == null || !"class".equals(token.key)) {
            return null;
        }

        token = tokenizer.nextToken();
        final Class<?> cl = Class.forName(token.value);
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);

        Map<String, XdStorageObjectField> props = propertiesCache.get(cl);
        if (props == null) {
            propertiesCache.put(cl, props = clInfo.getFields());
        }

        final Object result = cl.newInstance();

        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) {
                break;
            }

            token = tokenizer.nextToken();

            // =========================================================================
            // РЕГИСТРOНЕЗАВИСИМЫЙ ПЕРЕХВАТ JavaBeans ПОЛЕЙ:
            // Если прямое попадание props.get("hole") вернуло null из-за разницы регистров
            // кодогенерации СУБД, мы делаем fail-safe фолбэк по всей карте рефлексии!
            // =========================================================================
            XdStorageObjectField property = props.get(token.key);
            if (property == null) {
                // Ищем fail-safe совпадение без учета регистра (например, hole == Hole)
                for (Map.Entry<String, XdStorageObjectField> entry : props.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(token.key)) {
                        property = entry.getValue();
                        break;
                    }
                }
            }

            final XdStorageObjectIdField idField = clInfo.getIdField();

            Object tmp = null;
            if (token.value != null) {
                if (property != null) {
                    Class<?> targetType = normalizePrimitive(property.getFieldInfo().getClazz());
                    // =========================================================================
                    // ПОЛИМОРФНЫЙ БАРЬЕР СУБД: Если поле мапы или объекта имеет тип Object.class,
                    // мы принудительно трактуем скаляр как String, защищая ядро от NoSuchMethodException!
                    // =========================================================================
                    if (targetType == Object.class) {
                        targetType = String.class;
                    }
                    tmp = simpleTypeHelper.simpleTypeFromString(targetType, token.value);
                } else if (idField != null && token.key.equals(idField.getName())) {
                    Class<?> targetType = normalizePrimitive(idField.getFieldInfo().getClazz());
                    if (targetType == Object.class) {
                        targetType = String.class;
                    }
                    tmp = simpleTypeHelper.simpleTypeFromString(targetType, token.value);
                }
            } else {
                YamlLineToken nextBlock = tokenizer.peekToken();
                if (nextBlock != null) {
                    if ("enum".equals(nextBlock.key)) {
                        tmp = XdStorageYamlBlockContainersReader.readEnum(tokenizer);
                    } else if ("collection".equals(nextBlock.key)) {
                        tokenizer.nextToken(); // Чётко потребляем имя поля коллекции перед входом
                        tmp = XdStorageYamlBlockContainersReader.readCollection(tokenizer, nextBlock.level, simpleTypeHelper);
                    } else if ("map".equals(nextBlock.key)) {
                        tokenizer.nextToken(); // Чётко потребляем имя поля мапы перед входом
                        tmp = XdStorageYamlBlockContainersReader.readMap(tokenizer, nextBlock.level);
                    } else if ("reference".equals(nextBlock.key)) {
                        tmp = readReference(tokenizer, nextBlock.level, null, simpleTypeHelper);
                    } else if ("object".equals(nextBlock.key)) {
                        tokenizer.nextToken();
                        tmp = readObject(tokenizer, nextBlock.level, simpleTypeHelper);
                    }
                }
            }

            if (property != null && tmp != null) {
                property.set(result, tmp);
            } else if (idField != null && token.key.equals(idField.getName()) && tmp != null) {
                idField.set(result, tmp); // Навешиваем ID на инстанс Java
            }
        }
        return result;
    }

    public static Object readReference(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final XdStorageObjectIdField knownField, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        String className = null;
        String idValue = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) {
                break;
            }
            token = tokenizer.nextToken();
            if ("class".equals(token.key)) {
                className = token.value;
            } else if ("objectId".equals(token.key) || "dataStorageId".equals(token.key)) {
                idValue = token.value;
            }
        }

        if (className == null || idValue == null) return null;

        final Class<?> entityClass = Class.forName(className);
        final Object result = entityClass.newInstance();

        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(entityClass);
        final XdStorageObjectIdField idField = (knownField != null) ? knownField : clInfo.getIdField();

        Object objectId;
        Class<?> idClass = idField.getFieldInfo().getClazz();
        if (idClass == String.class) {
            objectId = idValue;
        } else {
            objectId = simpleTypeHelper.simpleTypeFromString(idClass, idValue);
        }

        idField.set(result, objectId);
        return result;
    }

    public static Object readObjectData(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        YamlLineToken token = tokenizer.nextToken();
        final Class<?> cl = Class.forName(token.value);
        final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(cl);
        final XdStorageObjectIdField idField = clInfo.getIdField();

        final XdStorageIdentifiableObject result = new XdStorageIdentifiableObject();
        result.setType(cl);

        Map<String, XdStorageObjectField> props = propertiesCache.get(cl);
        if (props == null) {
            propertiesCache.put(cl, props = clInfo.getFields());
        }

        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) {
                break;
            }
            token = tokenizer.nextToken();

            // Регистронезависимый поиск для контура метаданных
            XdStorageObjectField property = props.get(token.key);
            if (property == null) {
                for (Map.Entry<String, XdStorageObjectField> entry : props.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(token.key)) {
                        property = entry.getValue();
                        break;
                    }
                }
            }

            Object tmp = null;
            if (token.value != null) {
                Class<?> targetType = null;

                if (property != null) {
                    targetType = property.getFieldInfo().getClazz();
                } else if (idField != null && token.key.equals(idField.getName())) {
                    targetType = idField.getFieldInfo().getClazz();
                }

                if (targetType != null) {
                    targetType = normalizePrimitive(targetType);
                    // Снайперский перехват для мета-контура индексов
                    if (targetType == Object.class) {
                        targetType = String.class;
                    }
                    tmp = simpleTypeHelper.simpleTypeFromString(targetType, token.value);
                }
            } else {
                YamlLineToken nextBlock = tokenizer.peekToken();
                if (nextBlock != null) {
                    if ("object".equals(nextBlock.key)) {
                        tmp = readObjectData(tokenizer, nextBlock.level, simpleTypeHelper);
                    } else if ("enum".equals(nextBlock.key)) {
                        tmp = XdStorageYamlBlockContainersReader.readEnum(tokenizer);
                    } else if ("collection".equals(nextBlock.key)) {
                        tokenizer.nextToken(); // Чётко потребляем имя поля коллекции перед входом
                        tmp = XdStorageYamlBlockContainersReader.readObjectDataCollection(tokenizer, nextBlock.level, simpleTypeHelper);
                    } else if ("map".equals(nextBlock.key)) {
                        tokenizer.nextToken(); // Чётко потребляем имя поля мапы перед входом
                        tmp = XdStorageYamlBlockContainersReader.readObjectDataMap(tokenizer, nextBlock.level);
                    } else if ("reference".equals(nextBlock.key)) {
                        tmp = readObjectDataReference(tokenizer, nextBlock.level);
                    }
                }
            }

            if (idField != null && token.key.equals(idField.getName())) {
                result.setId(tmp);
            } else {
                result.setProperty(token.key, tmp);
            }
        }
        return result;
    }

    public static Object readObjectDataReference(final XdStorageYamlTokenizer tokenizer, final int parentLevel) throws Exception {
        String className = null;
        String idValue = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) {
                break;
            }
            token = tokenizer.nextToken();
            if ("class".equals(token.key)) {
                className = token.value;
            } else if ("objectId".equals(token.key) || "dataStorageId".equals(token.key)) {
                idValue = token.value;
            }
        }

        final XdStorageIdentifiableObject result = new XdStorageIdentifiableObject();
        if (className != null) {
            result.setType(Class.forName(className));
        }
        result.setId(idValue);
        return result;
    }

    private static Class<?> normalizePrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}