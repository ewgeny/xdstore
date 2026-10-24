package io.github.xdstorage;

import java.util.Collections;
import java.util.List;

/**
 * VERIFY (docs/tasks/2.1.0/26): the result of a read-only integrity walk over a storage
 * folder — {@code XdStorageProvider.verify(folder)}. The mechanized form of the manual
 * after-a-power-loss recipe (EMBEDDING.md § After a power loss): per-file sentinel
 * validity, journal state and record counts, per-class object counts, id-index entries
 * vs data objects, the format marker, and the folder-level findings (orphaned journals,
 * stale {@code .tmp} litter, count mismatches, a live folder lock).
 *
 * <p>Severity model (what the verdict means for "can I open this folder"): CORRUPT — a
 * v2 file without its EOF sentinel (opens loudly or serves garbage), an unparseable file,
 * a data-vs-index count mismatch; WARNINGS — damage the engine tolerates by design (a torn
 * journal tail discards only its un-sentineled tail, an orphaned journal replays, stale
 * {@code .tmp} files are swept at open, a legacy v1 file without a sentinel, a live lock
 * makes the results unreliable); OK — nothing to act on.
 *
 * <p>{@code verify} never repairs and never opens a storage; it reads files the way the
 * manual recipe's checks would and does not require (or take) the folder lock.
 */
public final class XdStorageVerifyReport {

    public enum Verdict { OK, WARNINGS, CORRUPT }

    /** Per-file structural state. */
    public enum State {
        /** The file parses and carries its EOF sentinel. */
        OK,
        /** Zero bytes — the legal empty-resource shape. */
        EMPTY,
        /** Content without the trailing EOF sentinel — truncated/torn (legacy v1 files legitimately lack it). */
        MISSING_SENTINEL,
        /** Journal-only: a sentinel is present but record blocks follow it — the torn tail a replay discards. */
        TORN_TAIL,
        /** The file cannot be tokenized at all. */
        PARSE_FAILED
    }

    /** One engine-owned file's verdict row. */
    public static final class FileReport {
        /** Folder-relative path, '/'-separated. */
        public final String path;
        /** base | index | structure | journal | btree | btree-node | references | marker. */
        public final String kind;
        public final State state;
        /** Structural entry count: objects/records for content files, journal records for journals. */
        public final long entries;
        public final long bytes;
        /** Journal files only: written under the 2.0.0-era stacked naming (auto-migrated at replay). */
        public final boolean legacyNaming;
        /** Non-fatal qualifications (e.g. "legacy v1 file without a sentinel"). */
        public final List<String> notes;

        public FileReport(final String path, final String kind, final State state, final long entries,
                          final long bytes, final boolean legacyNaming, final List<String> notes) {
            this.path = path;
            this.kind = kind;
            this.state = state;
            this.entries = entries;
            this.bytes = bytes;
            this.legacyNaming = legacyNaming;
            this.notes = notes == null ? Collections.<String>emptyList() : Collections.unmodifiableList(notes);
        }

        public boolean hasProblems() {
            return state != State.OK && state != State.EMPTY || !notes.isEmpty();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder(String.format("%-8s %-12s %6d entries %8d bytes  %s",
                    state, kind, entries, bytes, path));
            if (legacyNaming) {
                sb.append("  [legacy journal naming]");
            }
            for (final String note : notes) {
                sb.append("\n           note: ").append(note);
            }
            return sb.toString();
        }
    }

    /** The verified folder (as given). */
    public final String folder;
    public final Verdict verdict;
    /** The marker's formatVersion, or null when the marker is absent (legacy 1.x folder). */
    public final String formatVersion;
    /** A live storage holds the folder lock — the walk ran against files being written. */
    public final boolean inUse;
    /** Every engine-owned file inspected, sorted by path. */
    public final List<FileReport> files;
    /** Folder-level findings; every problem names its file or class directory. */
    public final List<String> findings;

    public XdStorageVerifyReport(final String folder, final Verdict verdict, final String formatVersion,
                                 final boolean inUse, final List<FileReport> files, final List<String> findings) {
        this.folder = folder;
        this.verdict = verdict;
        this.formatVersion = formatVersion;
        this.inUse = inUse;
        this.files = Collections.unmodifiableList(files);
        this.findings = Collections.unmodifiableList(findings);
    }

    public boolean isOk() {
        return verdict == Verdict.OK;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("verify ").append(folder).append(": ").append(verdict)
                .append(" (format v").append(formatVersion == null ? "?" : formatVersion).append(')');
        if (inUse) {
            sb.append(" [IN USE]");
        }
        for (final FileReport file : files) {
            if (file.hasProblems()) {
                sb.append("\n  ").append(file);
            }
        }
        for (final String finding : findings) {
            sb.append("\n  - ").append(finding);
        }
        if (files.isEmpty() && findings.isEmpty()) {
            sb.append("\n  (nothing engine-owned found)");
        }
        return sb.toString();
    }
}
