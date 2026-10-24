package io.github.xdstorage.search.key;

/** The operation tag carried in a {@code XdStorageSearchIndexKey}. The tag is documentary:
 * the key's ordering contract (its {@code compareTo}) deliberately ignores it, so entries
 * of every operation kind share one ordered tree topology per index.
 *
 * <p>Engine-internal — not part of the public API. Enum constants are immutable and safe
 * to share across threads. */
public enum XdStorageSearchIndexOperationType {

    /** Marks a search-tree entry written when an object is first indexed under a primary value. */
    Insert,

    /** Marks a search-tree entry rewritten in place when the primary value did not change. */
    Update,

    /** Marks the search-tree key removed when an object's primary value moves or the object is deleted. */
    Delete,

    /** Marks the key template built for an exact-match seek ({@code EQUALS}) or a range-bound seek. */
    Search,

    /** Marks the key kind associated with a full-index streaming scan (watch). */
    Watch;
}
