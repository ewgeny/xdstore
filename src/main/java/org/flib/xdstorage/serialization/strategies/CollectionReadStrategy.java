package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.serialization.XdStorageXmlHelper;
import java.util.Collection;
import java.util.Map;

/**
 * Стратегия десериализации коллекций (List, Set) из XML-тега &lt;collection&gt;.
 */
public class CollectionReadStrategy implements XdStorageXmlReadStrategy {

    @Override
    public boolean supports(final String elementName) {
        return "collection".equals(elementName);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object read(final String[] fieldName, final XMLStreamReader xmlReader, final XdStorageXmlReaderContext context) throws Exception {
        final Map<String, String> attrs = XdStorageXmlHelper.extractAttributes(xmlReader);
        fieldName[0] = attrs.get("name");
        final String className = attrs.get("class");

        // Динамически инстанцируем конкретную коллекцию (например, ArrayList или HashSet)
        final Collection<Object> collection = (Collection<Object>) Class.forName(className).getDeclaredConstructor().newInstance();
        final String[] fn = new String[]{null};

        while (xmlReader.hasNext()) {
            final int type = xmlReader.nextTag();
            if (type == XMLStreamConstants.END_ELEMENT && "collection".equals(xmlReader.getLocalName())) {
                break;
            }
            if (type != XMLStreamConstants.START_ELEMENT) {
                continue;
            }

            // Рекурсивно извлекаем элементы коллекции через общий контекст
            final Object item = context.readNextElement(xmlReader.getLocalName(), fn, xmlReader);
            collection.add(item);
        }
        return collection;
    }
}
