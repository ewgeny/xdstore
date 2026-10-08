package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Выделенный stateless-компонент для разбора Java Collections, Maps и Enums.
 * Извлекает элементы строго из объектной структуры - item: и entry: key/value.
 */
public class XdStorageYamlBlockContainersReader {

    public static Object readEnum(final XdStorageYamlTokenizer tokenizer) throws Exception {
        String className = null;
        String enumValue = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (!"class".equals(token.key) && !"value".equals(token.key)) {
                break;
            }
            token = tokenizer.nextToken();
            if ("class".equals(token.key)) {
                className = token.value;
            } else if ("value".equals(token.key)) {
                enumValue = token.value;
            }
        }

        if (className == null || enumValue == null) return null;

        final Class<?> cl = Class.forName(className);
        final Method m = cl.getMethod("valueOf", String.class);
        return m.invoke(cl, enumValue);
    }

    @SuppressWarnings("unchecked")
    public static Object readCollection(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        YamlLineToken classToken = tokenizer.nextToken();
        if (classToken == null || !"class".equals(classToken.key)) {
            return null;
        }

        Class<?> cl = Class.forName(classToken.value);
        Collection<Object> collection = (Collection<Object>) cl.newInstance();

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) break;

            if (token.isListItem && "item".equals(token.key)) {
                token = tokenizer.nextToken(); // Потребляем токен '- item:'
                Object itemValue = readTypedNode(tokenizer, token.level, simpleTypeHelper);
                collection.add(itemValue);
            } else {
                tokenizer.nextToken();
            }
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    public static Object readMap(final XdStorageYamlTokenizer tokenizer, IXdStorageSimpleTypeHelper simpleTypeHelper,
                                 final int parentLevel) throws Exception {
        YamlLineToken classToken = tokenizer.nextToken();
        if (classToken == null || !"class".equals(classToken.key)) {
            return null;
        }

        Class<?> cl = Class.forName(classToken.value);
        Map<Object, Object> map = (Map<Object, Object>) cl.newInstance();

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) break;

            if (token.isListItem && "entry".equals(token.key)) {
                token = tokenizer.nextToken(); // Потребляем токен '- entry:'

                int entryLevel = token.level;
                Object keyObj = null;
                Object valObj = null;

                YamlLineToken sub;
                while ((sub = tokenizer.peekToken()) != null) {
                    if (sub.level <= entryLevel) break;

                    if ("key".equals(sub.key)) {
                        tokenizer.nextToken(); // Потребляем 'key:'
                        keyObj = readTypedNode(tokenizer, sub.level, simpleTypeHelper);
                    } else if ("value".equals(sub.key)) {
                        tokenizer.nextToken(); // Потребляем 'value:'
                        valObj = readTypedNode(tokenizer, sub.level, simpleTypeHelper);
                    } else {
                        tokenizer.nextToken();
                    }
                }

                if (keyObj != null) {
                    map.put(keyObj, valObj);
                }
            } else {
                tokenizer.nextToken();
            }
        }
        return map;
    }

    private static Object readTypedNode(final XdStorageYamlTokenizer tokenizer, final int parentLevel,
                                        final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        String typeName = null;
        String rawScalar = null;
        YamlLineToken complexBlock = null;

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) break;

            // Ровно один честный вызов nextToken на итерацию цикла свойств!
            token = tokenizer.nextToken();

            if ("type".equals(token.key)) {
                typeName = token.value;
            } else if ("value".equals(token.key)) {
                if (token.value != null) {
                    // Случай А: Значение лежит на той же строчке (скаляр, например value: null)
                    rawScalar = token.value;
                } else {
                    // Случай Б: Значение разворачивается на следующей строке (сложный блок)
                    YamlLineToken next = tokenizer.peekToken();
                    if (next != null && next.level > token.level) {
                        complexBlock = next;
                        // =========================================================================
                        // СНАЙПЕРСКИЙ БАРЬЕР: Немедленно прерываем цикл сбора свойств элемента списка!
                        // Оставляем каретку токенизатора нетронутой прямо перед сложным блоком!
                        // =========================================================================
                        break;
                    }
                }
            }
        }

        // Выполняем проверку строго ПОСЛЕ того, как цикл полностью освободил поток
        if ("null".equals(typeName)) {
            return null;
        }

        if (rawScalar != null && !"null".equals(rawScalar)) {
            if (typeName != null) {
                Class<?> targetType = Class.forName(normalizePrimitiveName(typeName));
                if (targetType == Object.class) targetType = String.class;
                return simpleTypeHelper.simpleTypeFromString(targetType, rawScalar);
            }
            return rawScalar;
        }

        if (complexBlock != null) {
            if ("enum".equals(complexBlock.key)) {
                tokenizer.nextToken(); // Потребляем 'enum:' перед входом
                return readEnum(tokenizer);
            } else if ("object".equals(complexBlock.key)) {
                tokenizer.nextToken(); // Потребляем 'object:' перед входом
                return XdStorageYamlBlockObjectsReader.readObject(tokenizer, complexBlock.level, simpleTypeHelper);
            } else if ("reference".equals(complexBlock.key)) {
                return XdStorageYamlBlockObjectsReader.readReference(tokenizer, complexBlock.level, null, simpleTypeHelper);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static Object readObjectDataCollection(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        return readCollection(tokenizer, parentLevel, simpleTypeHelper);
    }

    @SuppressWarnings("unchecked")
    public static Object readObjectDataMap(final XdStorageYamlTokenizer tokenizer, IXdStorageSimpleTypeHelper simpleTypeHelper,
                                           final int parentLevel) throws Exception {
        return readMap(tokenizer, simpleTypeHelper, parentLevel);
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
}