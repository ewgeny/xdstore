package org.flib.xdstorage.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe write-out for resource files: content is written to a temporary
 * sibling file which then atomically replaces the target, so a crash can never
 * truncate an existing resource file halfway (the old delete-then-recreate
 * scheme lost the resource if the process died between the two steps).
 */
public final class XdStorageAtomicFile {

    private static final Logger log = LogManager.getLogger(XdStorageAtomicFile.class);

    private XdStorageAtomicFile() {
    }

    public static void writeReplacing(final String fileName, final ContentWriter content) throws IOException {
        final File target = new File(fileName);
        final File tmp = new File(fileName + ".tmp");
        if (tmp.exists()) {
            // незакрытый .tmp от предыдущего сбоя: безопасно удаляем (он не читается никем)
            log.warn("stale temp file {} found (previous crash mid-write) — removing", tmp);
            tmp.delete();
        }
        try {
            if (tmp.exists())
                tmp.delete();
            final File parentFile = tmp.getParentFile();
            if (parentFile != null && !parentFile.exists())
                parentFile.mkdirs();
            tmp.createNewFile();

            Writer writer = null;
            try {
                // Phase D: буферизация обязательна — сырой FileWriter делает syscall на каждый
                // append сериализации; на full-file write-out это доминирующая стоимость коммита
                writer = new java.io.BufferedWriter(new FileWriter(tmp), 1 << 16);
                content.write(writer);
            } finally {
                if (writer != null)
                    writer.close();
            }

            moveReplacing(tmp, target);
        } catch (final Exception e) {
            tmp.delete();
            if (e instanceof IOException)
                throw (IOException) e;
            throw new IOException("error by writing content of resource " + fileName, e);
        }
    }

    private static void moveReplacing(final File source, final File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            log.warn("atomic rename unsupported for {} — falling back to a non-atomic move "
                    + "(a crash during the swap may lose the file; consider a filesystem with rename support)", target);
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public interface ContentWriter {
        void write(Writer writer) throws Exception;
    }
}
