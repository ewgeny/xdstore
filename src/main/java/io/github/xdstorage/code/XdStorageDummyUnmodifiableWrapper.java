package io.github.xdstorage.code;

/**
 * Degradation stand-in for generated unmodifiable wrappers: used when the runtime has
 * no system Java compiler (a JRE), so no real wrapper subclass can be produced. Its
 * {@code equals}/{@code hashCode} contracts are broken for engine purposes (it is a
 * bare identity object); the loss is logged once and cached for the JVM lifetime.
 * Stateless and thread-safe. Engine-internal — not part of the public API.
 */
public class XdStorageDummyUnmodifiableWrapper implements IXdStorageUnmodifiableWrapper {

    /** Degrades to an inert stand-in: nothing to initialize. */
    public XdStorageDummyUnmodifiableWrapper() {
    }

    /**
     * Dummy-family marker mirroring the static marker generated into real unmodifiable wrappers.
     *
     * @return always {@code true} — this class is always the dummy variant
     */
    public static boolean isDummyUnmodifiableWrapper() {
        return true;
    }
}
