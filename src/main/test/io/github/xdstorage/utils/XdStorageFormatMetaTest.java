package io.github.xdstorage.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2: the on-disk format marker (`.xdstorage.meta`) — initialize / announce / legacy / newer-format.
 */
class XdStorageFormatMetaTest {

    @TempDir
    File folder;

    @Test
    void freshFolderGetsMarkerAndReopenAnnouncesSameVersion() {
        XdStorageFormatMeta.onOpen(folder, "db", "yaml");

        final Properties meta = XdStorageFormatMeta.read(folder);
        assertNotNull(meta, "marker must be written on a fresh folder");
        assertEquals(String.valueOf(XdStorageFormatMeta.FORMAT_VERSION), meta.getProperty("formatVersion"));
        assertEquals("yaml", meta.getProperty("format"));
        assertEquals("db", meta.getProperty("storage"));

        // reopen: same folder must be accepted, not re-initialized (marker intact)
        XdStorageFormatMeta.onOpen(folder, "db", "yaml");
        assertEquals(String.valueOf(XdStorageFormatMeta.FORMAT_VERSION),
                XdStorageFormatMeta.read(folder).getProperty("formatVersion"));
    }

    @Test
    void legacyFolderWithoutMarkerIsDetectedAndLeftUnwritten() throws IOException {
        // a 1.x-era folder: data content but no marker
        final File data = new File(folder, "data/Person");
        assertTrue(data.mkdirs());
        assertTrue(new File(data, "Person.xml").createNewFile());

        XdStorageFormatMeta.onOpen(folder, "db", "yaml");

        assertNull(XdStorageFormatMeta.read(folder),
                "a legacy folder must not silently gain a marker (detection stays honest)");
    }

    @Test
    void lockFileAndMarkerAloneDoNotCountAsStorageContent() throws IOException {
        assertTrue(new File(folder, XdStorageFolderLock.LOCK_FILE_NAME).createNewFile(),
                "precondition: lock file exists");
        XdStorageFormatMeta.onOpen(folder, "db", "yaml");
        assertNotNull(XdStorageFormatMeta.read(folder), "a folder with only the lock file is fresh, not legacy");
    }

    @Test
    void markerFromNewerLibraryIsRejectedLoudly() throws IOException {
        final Properties meta = new Properties();
        meta.setProperty("formatVersion", String.valueOf(XdStorageFormatMeta.FORMAT_VERSION + 1));
        meta.setProperty("format", "yaml");
        try (final FileWriter out = new FileWriter(new File(folder, XdStorageFormatMeta.META_FILE_NAME))) {
            meta.store(out, null);
        }

        final RuntimeException e = assertThrows(RuntimeException.class,
                () -> XdStorageFormatMeta.onOpen(folder, "db", "yaml"));
        assertTrue(e.getMessage().contains("upgrade xdstorage"), "rejection must say what to do: " + e.getMessage());
    }

    @Test
    void corruptMarkerIsRejectedLoudly() throws IOException {
        final Properties meta = new Properties();
        meta.setProperty("formatVersion", "not-a-number");
        try (final FileWriter out = new FileWriter(new File(folder, XdStorageFormatMeta.META_FILE_NAME))) {
            meta.store(out, null);
        }

        assertThrows(RuntimeException.class, () -> XdStorageFormatMeta.onOpen(folder, "db", "yaml"));
    }

    @Test
    void absentMarkerReadsAsNull() {
        assertFalse(new File(folder, XdStorageFormatMeta.META_FILE_NAME).exists());
        assertNull(XdStorageFormatMeta.read(folder));
    }
}
