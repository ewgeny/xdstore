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
 * Прецизионно выровнен по каретке Кнута для тотально типизированной метамодели.
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

        token = tokenizer.peekToken();
        if (token != null && "fields".equals(token.key)) {
            tokenizer.nextToken(); // Честно поглощаем 'fields:'

            int fieldsLevel = token.level;
            while ((token = tokenizer.peekToken()) != null) {
                if (token.level < fieldsLevel + 1) {
                    break;
                }

                if (token.isListItem && "field".equals(token.key)) {
                    token = tokenizer.nextToken(); // Честно поглощаем '- field:'
                    parseAndSetField(tokenizer, token.level, result, props, clInfo, simpleTypeHelper);
                } else {
                    tokenizer.nextToken();
                }
            }
        }
        return result;
    }

    private static void parseAndSetField(final XdStorageYamlTokenizer tokenizer, final int fieldLevel, final Object result,
                                         final Map<String, XdStorageObjectField> props, final XdStorageClassInfo clInfo,
                                         final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        String fieldName = null;
        String fieldTypeName = null;
        String rawScalarValue = null;
        YamlLineToken complexBlockToken = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= fieldLevel) {
                break;
            }

            token = tokenizer.nextToken();

            if ("name".equals(token.key)) {
                fieldName = token.value;
            } else if ("type".equals(token.key)) {
                fieldTypeName = token.value;
            } else if ("value".equals(token.key)) {
                if (token.value != null) {
                    rawScalarValue = token.value;
                } else {
                    YamlLineToken next = tokenizer.peekToken();
                    if (next != null && next.level > token.level) {
                        complexBlockToken = next;
                        break;
                    }
                }
            }
        }

        if (fieldName == null) return;

        XdStorageObjectField property = props.get(fieldName);
        if (property == null) {
            for (Map.Entry<String, XdStorageObjectField> entry : props.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(fieldName)) {
                    property = entry.getValue();
                    break;
                }
            }
        }

        final XdStorageObjectIdField idField = clInfo.getIdField();
        Object finalValue = null;

        if (rawScalarValue != null && !"null".equals(rawScalarValue)) {
            if (fieldTypeName != null && !"null".equals(fieldTypeName)) {
                Class<?> targetType = Class.forName(normalizePrimitiveName(fieldTypeName));
                if (targetType == Object.class) targetType = String.class;
                finalValue = simpleTypeHelper.simpleTypeFromString(targetType, rawScalarValue);
            } else {
                finalValue = rawScalarValue;
            }
        } else if (complexBlockToken != null) {
            if ("enum".equals(complexBlockToken.key)) {
                tokenizer.nextToken();
                finalValue = XdStorageYamlBlockContainersReader.readEnum(tokenizer);
            } else if ("collection".equals(complexBlockToken.key)) {
                tokenizer.nextToken();
                finalValue = XdStorageYamlBlockContainersReader.readCollection(tokenizer, fieldLevel, simpleTypeHelper);
            } else if ("map".equals(complexBlockToken.key)) {
                tokenizer.nextToken();
                // ФИКС СИГНАТУРЫ: Убран лишний параметр simpleTypeHelper!
                finalValue = XdStorageYamlBlockContainersReader.readMap(tokenizer, simpleTypeHelper, fieldLevel);
            } else if ("reference".equals(complexBlockToken.key)) {
                finalValue = readReference(tokenizer, complexBlockToken.level, null, simpleTypeHelper);
            } else if ("object".equals(complexBlockToken.key)) {
                tokenizer.nextToken();
                finalValue = readObject(tokenizer, complexBlockToken.level, simpleTypeHelper);
            }
        }

        if (property != null && finalValue != null) {
            property.set(result, finalValue);
        } else if (idField != null && fieldName.equals(idField.getName()) && finalValue != null) {
            idField.set(result, finalValue);
        }
    }

    public static Object readReference(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final XdStorageObjectIdField knownField, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        String className = null;
        String idValue = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) break;
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
            objectId = simpleTypeHelper.simpleTypeFromString(normalizePrimitive(idClass), idValue);
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

        token = tokenizer.peekToken();
        if (token != null && "fields".equals(token.key)) {
            tokenizer.nextToken();
            int fieldsLevel = token.level;
            while ((token = tokenizer.peekToken()) != null) {
                if (token.level < fieldsLevel + 1) break;

                if (token.isListItem && "field".equals(token.key)) {
                    token = tokenizer.nextToken();

                    String fName = null;
                    String fTypeName = null;
                    String rawScalar = null;
                    YamlLineToken complexBlock = null;

                    YamlLineToken subToken;
                    while ((subToken = tokenizer.peekToken()) != null) {
                        if (subToken.level <= token.level) break;

                        subToken = tokenizer.nextToken();
                        if ("name".equals(subToken.key)) {
                            fName = subToken.value;
                        } else if ("type".equals(subToken.key)) {
                            fTypeName = subToken.value;
                        } else if ("value".equals(subToken.key)) {
                            if (subToken.value != null) {
                                rawScalar = subToken.value;
                            } else {
                                YamlLineToken next = tokenizer.peekToken();
                                if (next != null && next.level > subToken.level) {
                                    complexBlock = next;
                                    break;
                                }
                            }
                        }
                    }

                    Object finalValue = null;
                    if (rawScalar != null && !"null".equals(rawScalar)) {
                        if (fTypeName != null) {
                            Class<?> targetType = Class.forName(normalizePrimitiveName(fTypeName));
                            if (targetType == Object.class) targetType = String.class;
                            finalValue = simpleTypeHelper.simpleTypeFromString(targetType, rawScalar);
                        } else {
                            finalValue = rawScalar;
                        }
                    } else if (complexBlock != null) {
                        if ("enum".equals(complexBlock.key)) {
                            tokenizer.nextToken();
                            finalValue = XdStorageYamlBlockContainersReader.readEnum(tokenizer);
                        } else if ("collection".equals(complexBlock.key)) {
                            tokenizer.nextToken();
                            finalValue = XdStorageYamlBlockContainersReader.readCollection(tokenizer, token.level, simpleTypeHelper);
                        } else if ("map".equals(complexBlock.key)) {
                            tokenizer.nextToken();
// ФИКС СИГНАТУРЫ МЕТАДАННЫХ: Убран лишний параметр simpleTypeHelper!
                            finalValue = XdStorageYamlBlockContainersReader.readMap(tokenizer, simpleTypeHelper, token.level);
                        } else if ("reference".equals(complexBlock.key)) {
                            finalValue = readReference(tokenizer, complexBlock.level, null, simpleTypeHelper);
                        } else if ("object".equals(complexBlock.key)) {
                            tokenizer.nextToken();
                            finalValue = readObject(tokenizer, complexBlock.level, simpleTypeHelper);
                        }
                    }

                    if (fName != null) {
                        if (idField != null && fName.equals(idField.getName())) {
                            result.setId(finalValue);
                        } else {
                            result.setProperty(fName, finalValue);
                        }
                    }
                } else {
                    tokenizer.nextToken();
                }
            }
        }
        return result;
    }

    public static Object readObjectDataReference(final XdStorageYamlTokenizer tokenizer, final int parentLevel) throws Exception {
        return readReference(tokenizer, parentLevel, null, new org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper());
    }

    private static String normalizePrimitiveName(String name) {
        if ("int".equals(name)) return "java.lang.Integer";
        if ("long".equals(name)) return "java.lang.Long";
        if ("boolean".equals(name)) return "java.lang.Boolean";
        if ("byte".equals(name)) return "java.lang.Byte";
        if ("short".equals(name)) return "java.lang.Short";
        if ("float".equals(name)) return "java.lang.Float";
        if ("double".equals(name)) return "java.lang.Double";
        if ("char".equals(name)) return "java.lang.Character";
        return name;
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

    public static String decode(final String value) {
        if (value == null) return null;
        // Восстанавливаем канонические кавычки Java, превращая пары '' обратно в одинарные '
        return value.replace("''", "'");
    }
}