package org.flib.xdstorage.serialization.yaml;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flib.xdstorage.exceptions.XdStorageIOException;
import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.resource.XdStorageObjectChange;
import org.flib.xdstorage.resource.XdStorageObjectOperationType;
import org.flib.xdstorage.services.XdStorageServicesLocator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * W-1: append-only commit journal for one resource (WRITE-PATH-DESIGN.md).
 *
 * <p>Layout: {@code <basefile>.journal.yaml} — a sequence of record blocks, each append
 * terminated by the S-1 EOF sentinel:</p>
 * <pre>
 * - record:
 *     serial: 17
 *     op: UPSERT
 *     id: 'ship-1'
 *     object:
 *         class: 'com.acme.Ship'
 *         ...full object body via the standard YAML object writer...
 * </pre>
 *
 * <p>Crash semantics: an append interrupted mid-write leaves the tail without a trailing
 * sentinel; the reader discards everything after the LAST sentinel (loud WARN) instead of
 * serving a torn record. Recovery replay is idempotent: UPSERT/DELETE by id, last record
 * in file order wins (appends are serialized by the resource commit lock, so file order is
 * commit order).</p>
 *
 * <p>Checkpoint (size threshold): full base rewrite via the existing writer + journal file
 * deletion. No checkpoint marker: replay over an already-checkpointed base is idempotent,
 * so every crash window between "base rewritten" and "journal deleted" is safe.</p>
 *
 * <p>W-1 scope notes: journaling covers data/reference resources only (id-index/search
 * B+tree infra keeps the full-file write-out — node graphs are not journalable records);
 * YAML format only.</p>
 */
public final class XdStorageYamlCommitJournal {

    private static final Logger log = LogManager.getLogger(XdStorageYamlCommitJournal.class);

    public static final String JOURNAL_SUFFIX = ".journal.yaml";

    private XdStorageYamlCommitJournal() {
    }

    public static Path journalFileFor(final String baseFileName) {
        return new File(baseFileName + JOURNAL_SUFFIX).toPath();
    }

    /** One durable redo/compensation record waiting to be appended. */
    public static final class Record {
        public final long serial;
        public final boolean delete;
        public final Object id;
        public final Object object;

        Record(final long serial, final boolean delete, final Object id, final Object object) {
            this.serial = serial;
            this.delete = delete;
            this.id = id;
            this.object = object;
        }
    }

    /**
     * Appends REDO records for a successfully prepared commit:
     * Insert/Update → UPSERT(newObject), Delete → DELETE(id).
     */
    public static void appendRedo(final Path file, final long commitSerial,
                                  final Collection<XdStorageObjectChange> changes,
                                  final XdStorageServicesLocator services,
                                  final XdStorageYamlIOFactory factory,
                                  final boolean durable) throws IOException, org.flib.xdstorage.exceptions.XdStorageException {
        final List<Record> records = new ArrayList<>();
        for (final XdStorageObjectChange change : changes) {
            if (change.id == null) {
                continue;   // deferred-id wrapper notification path — not a data record
            }
            if (change.type == XdStorageObjectOperationType.Delete) {
                records.add(new Record(commitSerial, true, change.id, null));
            } else {
                records.add(new Record(commitSerial, false, change.id, change.newObject));
            }
        }
        append(file, records, services, factory, durable);
    }

    /**
     * Appends COMPENSATION records for a rolled-back first phase: the restored (pre-tx
     * committed) state per changed id — oldObject → UPSERT, insert-without-old → DELETE.
     */
    public static void appendCompensation(final Path file,
                                          final Collection<XdStorageObjectChange> changes,
                                          final XdStorageServicesLocator services,
                                          final XdStorageYamlIOFactory factory,
                                          final boolean durable) throws IOException, org.flib.xdstorage.exceptions.XdStorageException {
        final List<Record> records = new ArrayList<>();
        for (final XdStorageObjectChange change : changes) {
            if (change.id == null) {
                continue;
            }
            if (change.oldObject != null) {
                records.add(new Record(0, false, change.id, change.oldObject));
            } else {
                records.add(new Record(0, true, change.id, null));
            }
        }
        append(file, records, services, factory, durable);
    }

    /** One synchronized append: records + trailing sentinel, O(delta) bytes. */
    private static synchronized void append(final Path file, final List<Record> records,
                                            final XdStorageServicesLocator services,
                                            final XdStorageYamlIOFactory factory,
                                            final boolean durable) throws IOException, org.flib.xdstorage.exceptions.XdStorageException {
        if (records.isEmpty()) {
            return;
        }
        final StringWriter out = new StringWriter();
        final XdStorageYamlEmitter emitter = new XdStorageYamlEmitter(out);
        final IXdStorageSimpleTypeHelper helper = factory.simpleTypeHelper();
        final IXdStorageIdGenerator idGenerator = factory.idGenerator();
        for (final Record record : records) {
            emitter.openBlock("- record");
            emitter.writeKey("serial");
            emitter.writeScalar(String.valueOf(record.serial));
            emitter.writeKey("op");
            emitter.writeScalar(record.delete ? "DELETE" : "UPSERT");
            emitter.writeKey("id");
            emitter.writeScalar("'" + XdStorageYamlBlockObjectsWriter.encode(helper.simpleTypeToString(record.id)) + "'");
            if (!record.delete && record.object != null) {
                emitter.openBlock("object");
                XdStorageYamlBlockObjectsWriter.writeObjectData(record.object, emitter, services, helper, idGenerator);
                emitter.closeBlock();
            }
            emitter.closeBlock();
        }
        emitter.writeEofSentinel();

        final byte[] bytes = out.toString().getBytes(Charset.defaultCharset());
        final File parent = file.getParent() != null ? file.getParent().toFile() : null;
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(file.toFile(), true)) {   // true = append
            fos.write(bytes);
            if (durable) {
                fos.getFD().sync();   // S-2 parity: the journal is the durability point when it holds the commit
            }
        }
    }

    /** One resource journal checkpoint entry: result of {@link #readForReplay}. */
    public static final class Replay {
        public final List<Object> upserts = new ArrayList<>();
        public final List<Object> deletes = new ArrayList<>();
        public int discardedTailRecords = 0;
    }

    /**
     * Reads the journal for recovery replay. Records after the last sentinel (a torn tail
     * from a crash mid-append) are DISCARDED with a loud WARN — unlike a truncated base
     * file (S-1: loud failure), the journal tail is by-design discardable because the
     * interrupted commit was never reported durable.
     */
    public static Replay readForReplay(final Path file, final Class<?> idType,
                                       final XdStorageYamlIOFactory factory) throws IOException {
        return readForReplay(file, idType, factory.simpleTypeHelper());
    }

    private static Replay readForReplay(final Path file, final Class<?> idType,
                                        final IXdStorageSimpleTypeHelper helper) throws IOException {
        final Replay replay = new Replay();
        if (!Files.exists(file)) {
            return replay;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile()), 1 << 16)) {
            final XdStorageYamlTokenizer tokenizer = new XdStorageYamlTokenizer(reader);
            final List<Object[]> pending = new ArrayList<>();   // [Boolean delete, Object idOrObject]
            boolean tornTail = false;
            try {
                YamlLineToken token;
                while ((token = tokenizer.nextToken()) != null) {
                    if (XdStorageYamlTokenizer.EOF_SENTINEL_KEY.equals(token.key)) {
                        for (final Object[] entry : pending) {
                            if ((Boolean) entry[0]) {
                                replay.deletes.add(entry[1]);
                            } else {
                                replay.upserts.add(entry[1]);
                            }
                        }
                        pending.clear();
                        continue;
                    }
                    if (token.level == 0 && token.isListItem && "record".equals(token.key)) {
                        pending.add(readRecordBody(tokenizer, idType, helper));
                    }
                }
                tornTail = !pending.isEmpty();
            } catch (final Exception parseFailure) {
                // обрыв внутри тела записи (обрезанный блок) — остаток после последнего сентинела отбрасываем
                tornTail = true;
            }
            if (tornTail) {
                replay.discardedTailRecords = pending.size();
                log.warn("commit journal {} has a TORN TAIL (crash mid-append): discarding {} record(s) after the last "
                        + "EOF sentinel — the interrupted commit was never reported durable", file, pending.size());
            }
        }
        return replay;
    }

    /** Reads one `- record` body; the record token itself is already consumed. */
    private static Object[] readRecordBody(final XdStorageYamlTokenizer tokenizer,
                                           final Class<?> idType,
                                           final IXdStorageSimpleTypeHelper helper) throws Exception {
        long serial = 0;
        boolean delete = false;
        String idScalar = null;
        Object payload = null;
        YamlLineToken token;
        while ((token = tokenizer.peekToken()) != null && token.level >= 1) {
            token = tokenizer.nextToken();
            if (token.level != 1) {
                continue;   // глубже уровня записи (не должен встречаться вне object-блока)
            }
            if (XdStorageYamlTokenizer.EOF_SENTINEL_KEY.equals(token.key)) {
                throw new XdStorageIOException("record block cut before its body");
            }
            if ("serial".equals(token.key)) {
                serial = token.value != null ? Long.parseLong(token.value) : 0;
            } else if ("op".equals(token.key)) {
                delete = "DELETE".equals(token.value);
            } else if ("id".equals(token.key)) {
                idScalar = token.value;
            } else if ("object".equals(token.key) && token.value == null) {
                payload = XdStorageYamlBlockObjectsReader.readObject(tokenizer, token.level, helper);
            }
        }
        if (idScalar == null) {
            throw new XdStorageIOException("journal record without id");
        }
        if (delete) {
            return new Object[]{true, helper.simpleTypeFromString(idType, idScalar)};
        }
        if (payload == null) {
            throw new XdStorageIOException("UPSERT record without object body");
        }
        return new Object[]{false, payload};
    }

    /** Checkpoint: the journal is dropped after the base file rewrite has been written atomically. */
    public static void reset(final Path file) throws IOException {
        Files.deleteIfExists(file);
    }

    public static long sizeOf(final Path file) {
        return Files.exists(file) ? file.toFile().length() : 0L;
    }
}
