package org.flib.xdstorage.serialization;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.exceptions.XdStorageRuntimeException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.utils.XdStorageClassInfo;
import org.flib.xdstorage.utils.XdStorageObjectIdField;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Высокоуровневый декомпозированный фасад JSON-читателя (Поинт В).
 * Делегирует разбор потока классу XdStorageJsonStreamLexer, а рефлексию — XdStorageJsonReflectionMapper.
 */
public class XdStorageJsonObjectsReader implements IXdStorageObjectsReader {

    private final XdStorageJsonReflectionMapper mapper;

    public XdStorageJsonObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.mapper = new XdStorageJsonReflectionMapper(simpleTypeHelper);
    }

    @Override
    public Collection<Object> readReferences(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException {
        return read(reader);
    }

    @Override
    public Collection<Object> read(final Reader reader) throws XdStorageIOException, XdStorageException {
        final Collection<Object> result = new ArrayList<>();
        try {
            // ИСПРАВЛЕНИЕ: Убираем зачистку сессии на каждый чих!
            // Это позволит мапперу склеить версии XdStorageBTree между последовательными вызовами read()
            // mapper.clearSessionCache();

            final String rawJson = XdStorageJsonStreamLexer.readAll(reader);
            if (rawJson.trim().isEmpty()) return result;

            final String json = XdStorageJsonStreamLexer.stripPrettyPrintFormatting(rawJson);

            int index = json.indexOf("[");
            if (index == -1) return result;

            int bracesCount = 0;
            StringBuilder objBuilder = new StringBuilder();
            for (int i = index + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '{') bracesCount++;
                if (bracesCount > 0) objBuilder.append(ch);
                if (ch == '}') {
                    bracesCount--;
                    if (bracesCount == 0) {
                        Object parsedObj = mapper.parseSingleJsonObject(objBuilder.toString());

                        if (parsedObj != null) {
                            try {
                                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(parsedObj.getClass());
                                if (clInfo != null && clInfo.getIdField() != null) {
                                    Object id = clInfo.getIdField().get(parsedObj);
                                    if (id != null) {
                                        result.add(parsedObj);
                                    }
                                } else {
                                    result.add(parsedObj);
                                }
                            } catch (Throwable t) {
                                result.add(parsedObj);
                            }
                        }
                        objBuilder.setLength(0);
                    }
                }
            }
        } catch (final XdStorageException e) {
            // ИСПРАВЛЕНИЕ: Проверяемые исключения ядра СУБД (включая concurrent modification)
            // пробрасываем наверх в неизменном виде! Это позволит MVCC контуру запустить ретрай транзакции.
            throw e;
        } catch (final XdStorageRuntimeException e) {
            // Если рантайм-исключение содержит внутри XdStorageException — вытаскиваем его
            if (e.getCause() instanceof XdStorageException) {
                throw (XdStorageException) e.getCause();
            }
            throw e;
        } catch (Throwable e) {
            throw new XdStorageIOException("Ошибка демаршалинга JSON структуры СУБД", e);
        }
        return result;
    }


    @Override
    public Collection<XdStorageIdentifiableObject> readData(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException {
        final Collection<XdStorageIdentifiableObject> result = new ArrayList<>();
        Collection<Object> objects = read(reader);
        for (Object obj : objects) {
            if (obj != null) {
                final XdStorageClassInfo clInfo = XdStorageObjectUtils.getClassInfo(obj.getClass());
                Object id = clInfo.getIdField().get(obj);

                if (id == null) {
                    continue;
                }

                XdStorageIdentifiableObject identifiable = new XdStorageIdentifiableObject();
                identifiable.setType(obj.getClass());
                identifiable.setId(id);

                clInfo.getFields().forEach((name, f) -> {
                    identifiable.setProperty(name, f.get(obj));
                });
                result.add(identifiable);
            }
        }
        return result;
    }
}
