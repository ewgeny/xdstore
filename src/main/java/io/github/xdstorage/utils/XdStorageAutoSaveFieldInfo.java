package io.github.xdstorage.utils;

import io.github.xdstorage.annotations.XdStorageAutoSave;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AUTOSAVE (docs/tasks/2.1.0/22): the per-class metadata of one {@link XdStorageAutoSave}
 * annotated field — built by {@link XdStorageClassMetadataRegistry} from the scanned fields
 * (inherited fields included, matching the scanner's superclass walk). Holds the field's
 * accessor (getter/setter via the owning class info) and the declared SHAPE (collection,
 * map, or single reference), which decides how the cascade extracts the child objects.
 *
 * <p>Map support (task 22 revision): a MAP-typed annotated field cascades its VALUES — and
 * only those value objects whose class carries an id-field ({@code XdStorageClassInfo.getIdField()
 * != null}); the KEYS of the map are never cascade candidates.
 */
public final class XdStorageAutoSaveFieldInfo {

    public enum Shape {
        COLLECTION, MAP, ARRAY, REFERENCE
    }

    private final XdStorageObjectField accessor;
    private final Shape shape;

    private XdStorageAutoSaveFieldInfo(final XdStorageObjectField accessor, final Shape shape) {
        this.accessor = accessor;
        this.shape = shape;
    }

    public XdStorageObjectField getAccessor() {
        return accessor;
    }

    public Shape getShape() {
        return shape;
    }

    public String getFieldName() {
        return accessor.getName();
    }

    /** true when the underlying reflection field carries the @XdStorageAutoSave annotation. */
    public static boolean isAutoSaveField(final Field field) {
        return field.isAnnotationPresent(XdStorageAutoSave.class);
    }

    /** Shape from the declared field type: Collection → COLLECTION, Map → MAP, array → ARRAY, else REFERENCE. */
    public static XdStorageAutoSaveFieldInfo of(final XdStorageObjectField accessor) {
        final Class<?> declared = accessor.field.getType();
        final Shape shape;
        if (Collection.class.isAssignableFrom(declared)) {
            shape = Shape.COLLECTION;
        } else if (Map.class.isAssignableFrom(declared)) {
            shape = Shape.MAP;
        } else if (declared.isArray()) {
            shape = Shape.ARRAY;
        } else {
            shape = Shape.REFERENCE;
        }
        return new XdStorageAutoSaveFieldInfo(accessor, shape);
    }

    /**
     * The cascade candidates of this field for the given owner instance — unwrapped values
     * ready for the engine's per-child checks. MAP shapes contribute their VALUES (never the
     * keys); values that are not id-field-bearing entities are filtered later by the engine's
     * id-field check, not here (the holder does not know the value classes).
     */
    public List<Object> childrenOf(final Object owner) {
        final Object value = accessor.get(owner);
        final List<Object> children = new ArrayList<>();
        if (value == null) {
            return children;
        }
        if (shape == Shape.COLLECTION) {
            for (final Object element : (Collection<?>) value) {
                if (element != null) {
                    children.add(element);
                }
            }
        } else if (shape == Shape.ARRAY) {
            // array elements are the child objects; the array itself is never a graph node
            for (int i = 0; i < java.lang.reflect.Array.getLength(value); ++i) {
                final Object element = java.lang.reflect.Array.get(value, i);
                if (element != null) {
                    children.add(element);
                }
            }
        } else if (shape == Shape.MAP) {
            for (final Object element : ((Map<?, ?>) value).values()) {
                if (element != null) {
                    children.add(element);
                }
            }
        } else {
            children.add(value);
        }
        return children;
    }
}
