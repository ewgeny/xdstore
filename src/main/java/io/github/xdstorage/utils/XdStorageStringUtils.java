package io.github.xdstorage.utils;

/**
 * Minimal string predicates shared by the engine (a leaner stand-in for the commons-lang
 * equivalents). Stateless and thread-safe. Engine-internal — not part of the public API.
 */
public final class XdStorageStringUtils {

    private XdStorageStringUtils() {
        // do nothing
    }

    /**
     * Tests for a blank string.
     *
     * @param str the string to test, may be {@code null}
     * @return {@code true} when the string is {@code null} or trims to the empty string
     */
    public static boolean isBlank(final String str) {
        return str == null || str.trim().length() == 0;
    }

    /**
     * Tests for an empty string.
     *
     * @param str the string to test, may be {@code null}
     * @return {@code true} when the string is {@code null} or has length 0 (whitespace counts as non-empty)
     */
    public static boolean isEmpty(final String str) { return  str == null || str.isEmpty(); }
}
