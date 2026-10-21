package io.github.xdstorage.reliability;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Test helper (not a test): simulates ANOTHER PROCESS holding the storage folder lock.
 * Locks the given lock file, signals readiness via a marker file, holds for the given
 * milliseconds, then exits — the parent test asserts open-failure-while-held and
 * open-success-after-exit.
 */
public class FolderLockHolderMain {

    public static void main(final String[] args) throws Exception {
        final Path lockFile = Paths.get(args[0]);
        final long holdMillis = Long.parseLong(args[1]);
        final Path readyMarker = Paths.get(args[2]);

        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            channel.lock();
            final ByteBuffer buffer = ByteBuffer.wrap("held-by-child-process".getBytes(StandardCharsets.UTF_8));
            channel.write(buffer);
            channel.force(false);
            Files.write(readyMarker, new byte[]{1});

            Thread.sleep(holdMillis);
        }
        System.exit(0);
    }
}
