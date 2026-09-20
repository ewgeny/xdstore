package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.serialization.XdStorageXmlHelper;
import java.util.Map;

/**
 * Стратегия десериализации карт (Map) из XML-структуры &lt;map&gt; с элементами &lt;entry&gt;.
 */
public class MapReadStrategy implements XdStorageXmlReadStrategy {

    @Override
    public boolean supports(final String elementName) {
        return "map".equals(elementName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object read(final String[] fieldName, final XMLStreamReader xmlReader, final XdStorageXmlReaderContext context) throws Exception {
        final Map<String, String> attrs = XdStorageXmlHelper.extractAttributes(xmlReader);
        fieldName[0] = attrs.get("name");
        final String className = attrs.get("class");

        final Map<Object, Object> map = (Map<Object, Object>) Class.forName(className).getDeclaredConstructor().newInstance();
        final String[] fn = new String[]{null};

        while (xmlReader.hasNext()) {
            final int type = xmlReader.nextTag();
            if (type == XMLStreamConstants.END_ELEMENT && "map".equals(xmlReader.getLocalName())) {
                break;
            }
            if (type != XMLStreamConstants.START_ELEMENT || !"entry".equals(xmlReader.getLocalName())) {
                continue;
            }

            // Вычитываем тег ключа <entry> -> <key_element>
            xmlReader.nextTag();
            final Object key = context.readNextElement(xmlReader.getLocalName(), fn, xmlReader);
            xmlReader.next(); // Пропускаем символы/конец элемента ключа

            // Вычитываем тег значения <value_element>
            xmlReader.nextTag();
            final Object value = context.readNextElement(xmlReader.getLocalName(), fn, xmlReader);

            map.put(key, value);
        }
        return map;
    }
}
