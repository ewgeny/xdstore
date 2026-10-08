package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.object.XdStorageIdentifiableObject;
import org.flib.xdstorage.serialization.IXdStorageObjectsReader;
import org.flib.xdstorage.transaction.IXdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectIdField;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Высокоуровневый транзакционный диспетчер демаршаллинга YAML.
 * Делегирует сборку блоков в специализированные компоненты.
 */
public class XdStorageYamlObjectsReader implements IXdStorageObjectsReader {

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;

    public XdStorageYamlObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this.simpleTypeHelper = simpleTypeHelper;
    }

    public Collection<Object> readObjects(final Reader reader) throws XdStorageIOException {
        Collection<Object> result = new ArrayList<>();
        try {
            XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(reader);

            YamlLineToken rootToken = tokenizer.nextToken();
            if (rootToken == null || !"objects".equals(rootToken.key)) {
                return result;
            }

            YamlLineToken token;
            while ((token = tokenizer.peekToken()) != null) {
                if (token.level <= 0) {
                    break;
                }

                token = tokenizer.nextToken();
                if (token.level == 1 && token.isListItem && "object".equals(token.key)) {
                    Object tmp = XdStorageYamlBlockObjectsReader.readObject(tokenizer, token.level, simpleTypeHelper);
                    if (tmp != null) {
                        result.add(tmp);
                    }
                }
            }
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
        return result;
    }

    @Override
    public Collection<Object> read(final Reader reader) throws XdStorageIOException, XdStorageException {
        return readObjects(reader);
    }

    @Override
    public Collection<Object> read(final Reader reader, final IXdStorageTransaction transaction) throws XdStorageIOException {
        return readObjects(reader);
    }

    @Override
    public Collection<Object> readReferences(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException {
        Collection<Object> result = new ArrayList<>();
        try {
            XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(reader);

            YamlLineToken rootToken = tokenizer.nextToken();
            if (rootToken == null || !"references".equals(rootToken.key)) {
                return result;
            }

            YamlLineToken token;
            while ((token = tokenizer.peekToken()) != null) {
                if (token.level <= 0) {
                    break;
                }

                token = tokenizer.nextToken();
                if (token.level == 1 && token.isListItem && "reference".equals(token.key)) {
                    Object tmp = XdStorageYamlBlockObjectsReader.readReference(tokenizer, token.level, field, simpleTypeHelper);
                    if (tmp != null) {
                        result.add(tmp);
                    }
                }
            }
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
        return result;
    }

    @Override
    public Collection<XdStorageIdentifiableObject> readData(final Reader reader, final XdStorageObjectIdField field) throws XdStorageIOException, XdStorageException {
        Collection<XdStorageIdentifiableObject> result = new ArrayList<>();
        try {
            XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(reader);
            YamlLineToken token;

            while ((token = tokenizer.nextToken()) != null) {
                if (token.level == 1) {
                    if ("object".equals(token.key)) {
                        result.add((XdStorageIdentifiableObject) XdStorageYamlBlockObjectsReader.readObjectData(tokenizer, token.level, simpleTypeHelper));
                    } else if ("reference".equals(token.key)) {
                        result.add((XdStorageIdentifiableObject) XdStorageYamlBlockObjectsReader.readObjectDataReference(tokenizer, token.level));
                    }
                }
            }
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
        return result;
    }
}
