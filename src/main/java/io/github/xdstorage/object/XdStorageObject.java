package io.github.xdstorage.object;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The in-memory record form of stored object data: the stored class plus a name-to-value
 * map of its fields. It is the shape in which the engine carries object data through the
 * DAO resources; {@code XdStorageIdentifiableObject} extends it with the record's id.
 * Property values are plain data (simple types, enums, defensively copied dates, nested
 * records for embedded children) — application entities are converted to and from this
 * form by {@code XdStorageObjectConverter}.
 *
 * <p>Instances are not synchronized and are not safe for concurrent modification.
 * Engine-internal — not part of the public API.</p>
 */
public class XdStorageObject {

    private Class<?> type;

    private Map<String, Object> properties;

    /**
     * Creates an empty record without type or properties.
     */
    public XdStorageObject() {
    }

    /**
     * Sets the stored class the record was created from.
     *
     * @param type the stored class the record was created from
     */
    public void setType(Class<?> type) {
        this.type = type;
    }

    /**
     * Returns the stored class the record was created from.
     *
     * @return the stored class the record was created from, or {@code null} when not set
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Replaces the whole properties map of the record.
     *
     * @param properties the record's field values keyed by field name
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Returns the whole properties map of the record.
     *
     * @return the record's field values keyed by field name, or {@code null} when no
     *         property has been set yet
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Reads one property value.
     *
     * @param name the field name to read
     * @return the property value, or {@code null} when the record holds no such property
     * @throws NullPointerException when no properties map has been set on the record yet
     */
    public Object getProperty(final String name) {
        return properties.get(name);
    }

    /**
     * Sets one property value, creating the properties map on first use.
     *
     * @param name   the field name to store the value under
     * @param object the property value
     */
    public void setProperty(final String name, final Object object) {
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, object);
    }

    /**
     * Records are equal when they carry the same type and the same properties.
     *
     * @param o the object to compare with
     * @return {@code true} when {@code o} is a {@code XdStorageObject} with equal type and
     *         properties
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XdStorageObject that = (XdStorageObject) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(properties, that.properties);
    }

    /**
     * @return a hash code derived from the record type and its properties
     */
    @Override
    public int hashCode() {
        return Objects.hash(type, properties);
    }
}
