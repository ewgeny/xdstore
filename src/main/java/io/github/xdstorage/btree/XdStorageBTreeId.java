package io.github.xdstorage.btree;

import java.util.Objects;

/**
 * Composite persistent identity of a B+tree index: the indexed entity class plus the index
 * name that distinguishes the several indexes possible over one class. Serves as the
 * {@code @XdStorageObjectId} of the persisted tree object itself; equality and hash code
 * cover both parts.
 *
 * <p>Engine-internal — not part of the public API.</p>
 */
public class XdStorageBTreeId {

    private Class<?> cl;

    private String name;

    /**
     * Default constructor for the persistence layer — an empty, unresolved identity.
     */
    public XdStorageBTreeId() {
        // do nothing
    }

    /**
     * Builds the identity of one index.
     *
     * @param cl   the indexed entity class
     * @param name the index name within that class
     */
    public XdStorageBTreeId(final Class<?> cl, final String name) {
        this.cl = cl;
        this.name = name;
    }

    /**
     * Returns the indexed entity class of this identity.
     *
     * @return the indexed entity class
     */
    public Class<?> getCl() {
        return cl;
    }

    /**
     * Sets the indexed entity class of this identity.
     *
     * @param cl the indexed entity class to set
     */
    public void setCl(Class<?> cl) {
        this.cl = cl;
    }

    /**
     * Returns the index name that distinguishes this index within the entity class.
     *
     * @return the index name within the entity class
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the index name of this identity.
     *
     * @param name the index name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Identity by both parts: the entity class and the index name.
     *
     * @param o the reference object to compare against
     * @return {@code true} when class and name both match
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XdStorageBTreeId that = (XdStorageBTreeId) o;
        return Objects.equals(cl, that.cl) &&
                Objects.equals(name, that.name);
    }

    /**
     * @return a hash code over both identity parts
     */
    @Override
    public int hashCode() {
        return Objects.hash(cl, name);
    }

    /**
     * {@code "<simple-class-name>-<index-name>"} — the human-readable form of the identity.
     *
     * @return the display form of this identity
     */
    @Override
    public String toString() {
        return cl.getSimpleName() + "-" + name;
    }
}
