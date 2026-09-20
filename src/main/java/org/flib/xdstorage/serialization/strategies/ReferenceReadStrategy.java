package org.flib.xdstorage.serialization.strategies;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import org.flib.xdstorage.serialization.XdStorageXmlHelper;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * Стратегия десериализации ленивых ссылок &lt;reference&gt; на другие объекты в БД.
 */
public class ReferenceReadStrategy implements XdStorageXmlReadStrategy {

    @Override
    public boolean supports(final String elementName) {
        return "reference".equals(elementName);
    }

    @Override
    public Object read(final String[] fieldName, final XMLStreamReader xmlReader, final XdStorageXmlReaderContext context) throws Exception {
        final Map<String, String> attrs = XdStorageXmlHelper.extractAttributes(xmlReader);
        fieldName[0] = attrs.get("name");
        final String className = attrs.get("class");
        final String classObjectId = attrs.get("classObjectId");

        // Поддерживаем совместимость со старыми атрибутами dataStorageId и objectId
        final String idValue = attrs.get("objectId") != null ? attrs.get("objectId") : attrs.get("dataStorageId");

        final Object result = Class.forName(className).getDeclaredConstructor().newInstance();
        final Class<?> idClass = Class.forName(classObjectId);

        final Object objectId = resolveId(idClass, idValue, xmlReader, context);
        final XdStorageObjectIdField field = XdStorageObjectUtils.getClassInfo(result.getClass()).getIdField();
        field.set(result, objectId);

        return result;
    }

    private Object resolveId(final Class<?> idClass, final String idValue, final XMLStreamReader xmlReader, final XdStorageXmlReaderContext context) throws Exception {
        if (idClass == Class.class) {
            return Class.forName(idValue);
        }
        if (XdStorageObjectUtils.isSimpleType(idClass, null)) {
            final Constructor<?> constructor = idClass.getConstructor(String.class);
            return constructor.newInstance(idValue);
        }

        // Граничное условие: если ID — это сложный составной объект, вычитываем его вложенным тегом
        while (xmlReader.hasNext()) {
            if (xmlReader.next() == XMLStreamConstants.START_ELEMENT && "object".equals(xmlReader.getLocalName())) {
                final String[] fn = new String[]{null};
                return context.readNextElement("object", fn, xmlReader);
            }
        }
        return null;
    }
}
