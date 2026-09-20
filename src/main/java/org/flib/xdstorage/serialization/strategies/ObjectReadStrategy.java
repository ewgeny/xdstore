package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.serialization.XdStorageXmlHelper;
import org.flib.xdstorage.utils.XdStorageObjectField;
import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Исправленная стратегия чтения сложных объектов. Обеспечивает полную совместимость с Java 17+.
 */
public class ObjectReadStrategy implements XdStorageXmlReadStrategy {

    @Override
    public boolean supports(final String elementName) {
        return "object".equals(elementName);
    }

    @Override
    public Object read(String[] fieldName, final XMLStreamReader xmlReader, final XdStorageXmlReaderContext context) throws Exception {
        final Map<String, String> attrs = XdStorageXmlHelper.extractAttributes(xmlReader);
        fieldName[0] = attrs.get("name");
        final String className = attrs.get("class");
        final String value = attrs.get("value");

        if (className == null) {
            xmlReader.nextTag();
            return null;
        }

        final Class<?> cl = Class.forName(className);
        if (value != null) {
            xmlReader.nextTag();
            return context.getSimpleTypeHelper().simpleTypeFromString(cl, value);
        }

        // ИСПРАВЛЕНИЕ СОВМЕСТИМОСТИ С JAVA 17+: Извлекаем конструктор по умолчанию напрямую
        final Constructor<?> constructor = cl.getDeclaredConstructor();
        constructor.setAccessible(true); // Защита от отсутствия публичного модификатора доступа
        final Object result = constructor.newInstance();

        // Запрашиваем дескрипторы свойств из безопасного конкурентного кэша контекста
        final Map<String, XdStorageObjectField> props = context.getFieldsCache(cl);

        while (xmlReader.hasNext()) {
            final int type = xmlReader.nextTag();
            if (type == XMLStreamConstants.END_ELEMENT && "object".equals(xmlReader.getLocalName())) {
                break;
            }
            if (type != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            final String[] fn = new String[]{null};
            final Object tmp = context.readNextElement(xmlReader.getLocalName(), fn, xmlReader);

            final XdStorageObjectField property = props.get(fn[0]);
            if (property != null) {
                property.set(result, tmp);
            }
        }

        return result;
    }
}
