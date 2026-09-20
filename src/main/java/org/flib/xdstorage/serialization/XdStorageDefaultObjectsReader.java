package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageObjectField;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;
import org.flib.xdstorage.serialization.strategies.*;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Безопасная и высокопроизводительная реализация XML-ридера объектов СУБД.
 * Защищена от XXE уязвимостей, XML-бомб и race conditions в многопоточной среде.
 */
public class XdStorageDefaultObjectsReader implements IXdStorageObjectsReader, XdStorageXmlReaderContext {

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final List<XdStorageXmlReadStrategy> strategies = new ArrayList<>();

    /**
     * Потокобезопасный кэш дескрипторов полей классов для исключения повторной тяжелой интроспекции.
     */
    private final Map<Class<?>, Map<String, XdStorageObjectField>> propertiesCache = new ConcurrentHashMap<>();

    public XdStorageDefaultObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.simpleTypeHelper = simpleTypeHelper;

        // Регистрация модулей-стратегий демаршалинга
        strategies.add(new ObjectReadStrategy());
        strategies.add(new ArrayReadStrategy());
        strategies.add(new CollectionReadStrategy());
        strategies.add(new MapReadStrategy());
        strategies.add(new ReferenceReadStrategy());
    }

    @Override
    public Collection<Object> read(final Reader reader) throws XdStorageIOException {
        return readStream(reader, xmlReader -> {
            String name = xmlReader.getLocalName();
            if ("object".equals(name) || "reference".equals(name)) {
                String[] fn = new String[]{null};
                return readNextElement(name, fn, xmlReader);
            }
            return null;
        });
    }

    /**
     * Инкапсулирует логику безопасного вычитывания XML-потока.
     */
    private Collection<Object> readStream(final Reader reader, final XmlElementExtractor extractor) throws XdStorageIOException {
        Collection<Object> result = new ArrayList<>();
        try {
            // ИСПРАВЛЕНИЕ ОШИБКИ БЕЗОПАСНОСТИ (XXE): Инициализируем фабрику парсера
            XMLInputFactory factory = XMLInputFactory.newInstance();

            // Жестко отключаем обработку внешних DTD и деклараций сущностей
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);

            XMLStreamReader xmlReader = factory.createXMLStreamReader(reader);
            while (xmlReader.hasNext()) {
                if (xmlReader.next() == XMLStreamConstants.START_ELEMENT) {
                    Object tmp = extractor.extract(xmlReader);
                    if (tmp != null) {
                        result.add(tmp);
                    }
                }
            }
        } catch (final Throwable cause) {
            throw new XdStorageIOException("Ошибка безопасного парсинга XML-потока", cause);
        }
        return result;
    }

    @Override
    public Object readNextElement(String elementName, String[] fieldName, XMLStreamReader xmlReader) throws Exception {
        for (int i = 0; i < strategies.size(); i++) {
            XdStorageXmlReadStrategy strategy = strategies.get(i);
            if (strategy.supports(elementName)) {
                return strategy.read(fieldName, xmlReader, this);
            }
        }
        return null;
    }

    @Override
    public Map<String, XdStorageObjectField> getFieldsCache(Class<?> clazz) {
        // ИСПРАВЛЕНИЕ RACE CONDITION: Атомарная сборка свойств без риска бесконечных циклов
        return propertiesCache.computeIfAbsent(clazz,
                cl -> XdStorageObjectUtils.getClassInfo(cl).getFields());
    }

    @Override
    public IXdStorageSimpleTypeHelper getSimpleTypeHelper() {
        return simpleTypeHelper;
    }

    @Override
    public Collection<Object> readReferences(Reader r, XdStorageObjectIdField f) {
        throw new UnsupportedOperationException("Перенесено в декомпозированную структуру ReferenceReadStrategy");
    }

    @Override
    public Collection<XdStorageIdentifiableObject> readData(Reader r, XdStorageObjectIdField f) {
        throw new UnsupportedOperationException("Перенесено в специализированный подкласс метаданных");
    }

    @FunctionalInterface
    private interface XmlElementExtractor {
        Object extract(XMLStreamReader reader) throws Exception;
    }
}
