package io.github.xdstorage.object;

import io.github.xdstorage.annotations.XdStorageObjectId;

import java.util.Objects;

/**
 * An id-tagged stored record: a {@code XdStorageObject} carrying the value of the class's
 * {@code @XdStorageObjectId} field. This is the addressable form of a record — the
 * id-keyed resources, references and index payloads all hold it, and the object converter
 * produces it from application entities and materializes entities back from it.
 *
 * <p>Instances are not synchronized and are not safe for concurrent modification.
 * Engine-internal — not part of the public API.</p>
 */
public class XdStorageIdentifiableObject extends XdStorageObject {

    @XdStorageObjectId
    private Object id;

    /**
     * Creates an empty record without id or properties.
     */
    public XdStorageIdentifiableObject() {
    }

    /**
     * Returns the stored object's id value.
     *
     * @return the stored object's id value, or {@code null} when not set
     */
    public Object getId() {
        return id;
    }

    /**
     * Sets the stored object's id value.
     *
     * @param id the stored object's id value
     */
    public void setId(Object id) {
        this.id = id;
    }

    /**
     * Records are equal when they are of the same runtime class with the same id and the
     * same properties.
     *
     * @param o the object to compare with
     * @return {@code true} when {@code o} is a {@code XdStorageIdentifiableObject} with
     *         equal id and equal record content
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XdStorageIdentifiableObject that = (XdStorageIdentifiableObject) o;
        return Objects.equals(id, that.id) && super.equals(o);
    }

    /**
     * @return a hash code derived from the record content and the id
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id);
    }
}
