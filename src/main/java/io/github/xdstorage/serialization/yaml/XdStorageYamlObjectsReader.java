package io.github.xdstorage.serialization.yaml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.github.xdstorage.exceptions.XdStorageException;
import io.github.xdstorage.exceptions.XdStorageIOException;
import io.github.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import io.github.xdstorage.object.XdStorageIdentifiableObject;
import io.github.xdstorage.serialization.IXdStorageObjectsReader;
import io.github.xdstorage.transaction.IXdStorageTransaction;
import io.github.xdstorage.utils.XdStorageObjectIdField;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Высокоуровневый транзакционный диспетчер демаршаллинга YAML.
 * Делегирует сборку блоков в специализированные компоненты.
 *
 * <p>S-1 (truncation detection): в строгом режиме ({@code strictEof = true}) файл без
 * сентинела конца файла {@link XdStorageYamlTokenizer#EOF_SENTINEL_LINE} отвергается —
 * обрезанный ресурс НЕ загружается молча как частичная база. Движок включает строгий
 * режим по умолчанию (см. {@code XdStorageConfig.acceptMissingEofSentinel} для миграции
 * легаси-хранилищ). Прямое конструирование в компонентных тестах по умолчанию
 * нестрогое (легаси-фикстуры без сентинела читаются как раньше).</p>
 */
public class XdStorageYamlObjectsReader implements IXdStorageObjectsReader {

    private static final Logger log = LogManager.getLogger(XdStorageYamlObjectsReader.class);

    private final IXdStorageSimpleTypeHelper simpleTypeHelper;
    private final boolean strictEof;

    public XdStorageYamlObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper) {
        this(simpleTypeHelper, false);
    }

    public XdStorageYamlObjectsReader(final IXdStorageSimpleTypeHelper simpleTypeHelper, final boolean strictEof) {
        this.simpleTypeHelper = simpleTypeHelper;
        this.strictEof = strictEof;
    }

    public Collection<Object> readObjects(final Reader reader) throws XdStorageIOException {
        Collection<Object> result = new ArrayList<>();
        try {
            XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(reader);

            YamlLineToken rootToken = tokenizer.nextToken();
            if (rootToken == null || !"objects".equals(rootToken.key)) {
                // пустой файл (0 байт) = пустой ресурс; корневой блок без содержимого
                // и без сентинела отсекается verifyCleanEof ниже
                if (rootToken == null) {
                    return result;
                }
                verifyCleanEof(tokenizer, 0, "objects");
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
            verifyCleanEof(tokenizer, result.size(), "objects");
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
                if (rootToken == null) {
                    return result;
                }
                verifyCleanEof(tokenizer, 0, "references");
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
            verifyCleanEof(tokenizer, result.size(), "references");
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
            boolean sentinelSeen = false;

            while ((token = tokenizer.nextToken()) != null) {
                if (XdStorageYamlTokenizer.EOF_SENTINEL_KEY.equals(token.key)) {
                    sentinelSeen = true;
                    break;
                }
                if (token.level == 1) {
                    if ("object".equals(token.key)) {
                        result.add((XdStorageIdentifiableObject) XdStorageYamlBlockObjectsReader.readObjectData(tokenizer, token.level, simpleTypeHelper));
                    } else if ("reference".equals(token.key)) {
                        result.add((XdStorageIdentifiableObject) XdStorageYamlBlockObjectsReader.readObjectDataReference(tokenizer, token.level));
                    }
                }
            }

            if (!sentinelSeen) {
                if (strictEof) {
                    throw new XdStorageIOException(truncationMessage(result.size(), "data"));
                }
                log.warn("resource file has no EOF sentinel after {} data record(s) — legacy file, accepted as complete "
                        + "(enable strictness by rewriting the storage with a current engine version)", result.size());
            }
        } catch (final Throwable cause) {
            throw new XdStorageIOException(cause);
        }
        return result;
    }

    /**
     * S-1: главный цикл readObjects/readReferences останавливается на токене уровня <= 0
     * (который остался в буфере заглядывания) либо на EOF. Потребляем его и требуем сентинел.
     */
    private void verifyCleanEof(final XdStorageYamlTokenizer tokenizer, final int objectsRead, final String root)
            throws XdStorageIOException, java.io.IOException {
        final YamlLineToken trailing = tokenizer.nextToken();
        final boolean clean = trailing != null && XdStorageYamlTokenizer.EOF_SENTINEL_KEY.equals(trailing.key);
        if (!clean) {
            if (strictEof) {
                throw new XdStorageIOException(truncationMessage(objectsRead, root)
                        + (trailing != null ? " (unexpected trailing token: " + trailing + ")" : ""));
            }
            log.warn("resource file has no EOF sentinel after {} object(s) in '{}' block — legacy file, accepted as complete",
                    objectsRead, root);
        }
    }

    private String truncationMessage(final int objectsRead, final String root) {
        return "resource file is TRUNCATED or corrupted: no EOF sentinel after " + objectsRead
                + " record(s) in '" + root + "' block — refusing to serve a partial database "
                + "(the file was likely cut by a crash or an external process)";
    }
}
