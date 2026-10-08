package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

/**
 * Выделенный stateless-компонент для разбора Java Collections, Maps и Enums.
 * Полностью универсален, работает динамически через рефлексию без хардкода констант.
 */
public class XdStorageYamlBlockContainersReader {

    public static Object readEnum(final XdStorageYamlTokenizer tokenizer) throws Exception {
        tokenizer.nextToken(); // Потребляем 'enum:'

        String className = null;
        String enumValue = null;

        // Динамический разбор свойств энума с защитой от изменения порядка строк Райтера
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
            token = tokenizer.nextToken();

            Object tmp = null;
            if (token.isListItem) {
                if ("null".equals(token.key)) {
                    tmp = null;
                } else if ("object".equals(token.key)) {
                    tmp = XdStorageYamlBlockObjectsReader.readObject(tokenizer, token.level, simpleTypeHelper);
                } else if ("reference".equals(token.key)) {
                    tmp = XdStorageYamlBlockObjectsReader.readReference(tokenizer, token.level, null, simpleTypeHelper);
                } else {
                    tmp = token.value != null ? token.value : token.key;
                }
            } else if (token.value != null) {
                tmp = token.value;
            }
            collection.add(tmp);
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    public static Object readMap(final XdStorageYamlTokenizer tokenizer, final int parentLevel) throws Exception {
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
                tokenizer.nextToken(); // Потребляем токен '- entry:'

                tokenizer.nextToken(); // Потребляем токен 'key:'
                Object keyObj = readMapComponent(tokenizer, token.level + 1);

                tokenizer.nextToken(); // Потребляем токен 'value:'
                Object valObj = readMapComponent(tokenizer, token.level + 1);

                if (keyObj != null) {
                    map.put(keyObj, valObj);
                }
            } else {
                tokenizer.nextToken();
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Object readObjectDataCollection(final XdStorageYamlTokenizer tokenizer, final int parentLevel, final IXdStorageSimpleTypeHelper simpleTypeHelper) throws Exception {
        YamlLineToken classToken = tokenizer.nextToken();
        if (classToken == null || !"class".equals(classToken.key)) {
            return null;
        }

        Class<?> cl = Class.forName(classToken.value);
        Collection<Object> collection = (Collection<Object>) cl.newInstance();

        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null) {
            if (token.level <= parentLevel) break;
            token = tokenizer.nextToken();

            Object tmp = null;
            if (token.isListItem) {
                if ("null".equals(token.key)) {
                    tmp = null;
                } else if ("object".equals(token.key)) {
                    tmp = XdStorageYamlBlockObjectsReader.readObjectData(tokenizer, token.level, simpleTypeHelper);
                } else if ("reference".equals(token.key)) {
                    tmp = XdStorageYamlBlockObjectsReader.readObjectDataReference(tokenizer, token.level);
                } else {
                    tmp = token.value != null ? token.value : token.key;
                }
            } else if (token.value != null) {
                tmp = token.value;
            }
            collection.add(tmp);
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    public static Object readObjectDataMap(final XdStorageYamlTokenizer tokenizer, final int parentLevel) throws Exception {
        return readMap(tokenizer, parentLevel);
    }

    /**
     * Универсальный адаптивный метод вычитки ключа или значения Map.
     * Полностью очищен от хардкода, восстанавливает инстансы рефлексивно на лету.
     */
    private static Object readMapComponent(final XdStorageYamlTokenizer tokenizer, final int parentLevel) throws Exception {
        YamlLineToken nextToken = tokenizer.peekToken();
        if (nextToken == null) return null;

        // Если это плоский атомарный скаляр (например, строка в кавычках)
        if (nextToken.value != null) {
            YamlLineToken t = tokenizer.nextToken();
            return "null".equals(t.value) ? null : t.value;
        }

        // Если это вложенный блок метаданных типа
        if (nextToken.level > parentLevel - 1) {
            if ("enum".equals(nextToken.key)) {
                return readEnum(tokenizer);
            } else if ("object".equals(nextToken.key)) {
                tokenizer.nextToken(); // Потребляем 'object:'
                return XdStorageYamlBlockObjectsReader.readObject(tokenizer, nextToken.level, new org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper());
            } else if ("reference".equals(nextToken.key)) {
                return XdStorageYamlBlockObjectsReader.readReference(tokenizer, nextToken.level, null, new org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper());
            }
        }

        // Fail-safe фолбэк для пустых строк/ключей без двоеточий
        YamlLineToken t = tokenizer.nextToken();
        return t.key;
    }
}
