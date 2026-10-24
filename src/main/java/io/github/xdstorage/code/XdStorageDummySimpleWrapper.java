package io.github.xdstorage.code;

/**
 * Degradation stand-in for generated simple wrappers: used when the runtime has no
 * system Java compiler (a JRE), so no real wrapper subclass can be produced. Its
 * {@code equals}/{@code hashCode} contracts are broken for engine purposes (it is a
 * bare identity object); the loss is logged once and cached for the JVM lifetime.
 * Stateless and thread-safe. Engine-internal — not part of the public API.
 */
public class XdStorageDummySimpleWrapper implements IXdStorageSimpleWrapper {

    /** Degrades to an inert stand-in: nothing to initialize. */
    public XdStorageDummySimpleWrapper() {
    }

    /**
     * Dummy-family marker mirroring the static marker generated into real simple wrappers.
     *
     * @return always {@code true} — this class is always the dummy variant
     */
    public static boolean isDummySimpleWrapper() {
        return true;
    }

    /**
     * Degrades to a constant: the dummy wraps nothing.
     *
     * @return always {@code null}
     */
    @Override
    public Object getObjectId__() {
        return null;
    }

    /**
     * No-op: the dummy keeps no reference state.
     *
     * @param reference ignored
     */
    @Override
    public void setReference__(boolean reference) {
        // do nothing
    }

    /**
     * Degrades to a constant: the dummy is never a reference.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isReference__() {
        // do nothing
        return false;
    }

    /**
     * No-op: the dummy carries no lock.
     */
    @Override
    public void lock__() {
        // do nothing
    }

    /**
     * No-op: the dummy carries no lock.
     */
    @Override
    public void unlock__() {
        // do nothing
    }
}
