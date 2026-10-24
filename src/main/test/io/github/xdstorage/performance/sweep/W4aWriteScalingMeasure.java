package io.github.xdstorage.performance.sweep;

import io.github.xdstorage.IXdFileStorage;
import io.github.xdstorage.XdStorageConfig;
import io.github.xdstorage.XdStorageProvider;
import io.github.xdstorage.reliability.IndexJournalEntity;
import io.github.xdstorage.transaction.IXdStorageTransaction;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * W-4a acceptance measurement (docs/tasks/2.1.0/32, W4-DESIGN.md § acceptance):
 * the write-scaling wall before/after index journaling.
 *
 * <p>For each dataset size N: a bulk insert of N entities in ONE transaction (the commit
 * whose phase-1 touches the id-index resource), then a burst of 200 single-object save
 * transactions — the "small write at dataset size N" cost, which is where the O(N_ids)
 * full-file rewrite of the hash id-index shows. Run twice:
 * {@code java ... W4aWriteScalingMeasure off} and {@code on}.
 *
 * <p>Not JMH, not canonical: this is the W-4a gate measurement (single run, warm machine
 * assumed) — the canonical suite integrates the scenario after the gate passes.
 */
public final class W4aWriteScalingMeasure {

    private W4aWriteScalingMeasure() {
    }

    public static void main(final String[] args) throws Exception {
        final boolean journalOn = args.length > 0 && "on".equals(args[0]);
        System.out.println("=== W-4a write scaling: indexJournalWrites=" + journalOn + " ===");
        for (final int n : new int[]{1_000, 5_000, 50_000}) {
            measure(n, journalOn);
        }
    }

    private static void measure(final int n, final boolean journalOn) throws Exception {
        final Path folder = Files.createTempDirectory("w4a-" + n + "-" + (journalOn ? "on" : "off"));
        final String name = "w4a-" + n + "-" + (journalOn ? "on" : "off") + "-" + System.nanoTime();
        final XdStorageConfig config = XdStorageConfig.builder()
                .indexJournalWrites(journalOn)
                .defaultTransactionTimeoutMillis(600_000L)
                .build();
        final IXdFileStorage storage = XdStorageProvider.newOrGetFileStorage(name, folder.toString(), 128, config);
        try {
            // 1. the bulk insert: one transaction, N objects — the commit that touches the index
            final long bulkStart = System.nanoTime();
            final IXdStorageTransaction tx = storage.beginTransaction(600_000L);
            for (int i = 0; i < n; i++) {
                storage.save(new IndexJournalEntity((long) i, "entity-" + i), tx);
            }
            storage.commitTransaction(tx);
            final long bulkMs = (System.nanoTime() - bulkStart) / 1_000_000L;

            // 2. the small-write burst at dataset size N: 200 single-object save transactions
            final int burst = 200;
            final long burstStart = System.nanoTime();
            for (int i = 0; i < burst; i++) {
                final IXdStorageTransaction smallTx = storage.beginTransaction(600_000L);
                storage.save(new IndexJournalEntity((long) n + i, "small-" + i), smallTx);
                storage.commitTransaction(smallTx);
            }
            final long burstMs = (System.nanoTime() - burstStart) / 1_000_000L;

            System.out.printf("N=%6d: bulk-insert commit=%6d ms | small write=%6.3f ms/tx (%d txs)%n",
                    n, bulkMs, (double) burstMs / burst, burst);
        } finally {
            storage.shutdown();
        }
    }
}
