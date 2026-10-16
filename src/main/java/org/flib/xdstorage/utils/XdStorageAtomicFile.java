package org.flib.xdstorage.utils;

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

    private XdStorageAtomicFile() {
    }

    public static void writeReplacing(final String fileName, final ContentWriter content) throws IOException {
        final File target = new File(fileName);
        final File tmp = new File(fileName + ".tmp");
        try {
            if (tmp.exists())
                tmp.delete();
            final File parentFile = tmp.getParentFile();
            if (parentFile != null && !parentFile.exists())
                parentFile.mkdirs();
            tmp.createNewFile();

            Writer writer = null;
            try {
                writer = new FileWriter(tmp);
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
            Files.move(source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public interface ContentWriter {
        void write(Writer writer) throws Exception;
    }
}
