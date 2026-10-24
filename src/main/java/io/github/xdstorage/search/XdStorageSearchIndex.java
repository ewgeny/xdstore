package io.github.xdstorage.search;

import io.github.xdstorage.utils.*;

import java.util.*;

/** The declared-index descriptor: everything the engine needs to build index records for
 * one declared index of one entity class — the index name, the id field, the PRIMARY indexed
 * field (the first declared index field: it becomes the search tree's ordering key), the
 * accessors of the remaining indexed fields, and the accessors of nested child entities
 * (with the child-class index to route deeper indexing to).
 *
 * <p>Populated once during class introspection from the entity's annotations, then only
 * read by the index-record assembly and the resource layer. Not safe for concurrent
 * modification while being populated.
 *
 * <p>Engine-internal — not part of the public API. */
public class XdStorageSearchIndex {

    private String name;

    private int indexFillingValue;

    private XdStorageObjectIdField idField;

    private XdStorageObjectField primaryField;

    private Map<String, XdStorageObjectField> fieldAccesors;

    /**
     * map[ child_field, child_class_index ]
     */
    private Map<String, String> childrenIndexes;

    /**
     * map[ child_class, list[ pair[field_name, child_accessor ] ] ]
     */
    private Map<Class<?>, List<XdStoragePair<String, XdStorageObjectField>>> childrenAccessors;

    /**
     * map[ child_class, child_id_field_accessor ]
     */
    private Map<Class<?>, XdStorageObjectIdField> childrenIdFields;

    /**
     * map[ child_class, map[ child_field_name, child_field_accessor ]
     */
    private Map<Class<?>, Map<String, XdStorageObjectField>> childrenFieldAccessors;

    /**
     * Builds the descriptor's fixed part.
     *
     * @param idField           the indexed entity's id field accessor
     * @param name              the index name (unique per entity class)
     * @param indexFillingValue the fill value the index's B+trees are created with
     *                          (the minimum-degree parameter {@code t}; must exceed 1)
     */
    public XdStorageSearchIndex(final XdStorageObjectIdField idField, final String name, final int indexFillingValue) {
        this.name = name;
        this.indexFillingValue = indexFillingValue;
        this.idField = idField;
    }

    /**
     * The index name.
     *
     * @return the index name (unique per entity class)
     */
    public String getName() {
        return name;
    }

    /**
     * The fill value the index's B+trees are created with (the minimum-degree
     * parameter {@code t}).
     *
     * @return the fill value
     */
    public int getIndexFillingValue() {
        return indexFillingValue;
    }

    /**
     * The indexed entity's id field accessor.
     *
     * @return the id field accessor
     */
    public XdStorageObjectIdField getIdField() {
        return idField;
    }

    /**
     * Declares the PRIMARY indexed field — the first declared index field, whose values
     * become the search tree's ordering keys. Required before index records can be built.
     *
     * @param primaryField the primary field accessor
     */
    public void setPrimaryField(final XdStorageObjectField primaryField) {
        this.primaryField = primaryField;
    }

    /**
     * The PRIMARY indexed field — the source of the search tree's ordering keys.
     *
     * @return the primary field accessor
     */
    public XdStorageObjectField getPrimaryField() {
        return primaryField;
    }

    /**
     * Adds an accessor for one of the index's (non-primary) declared fields.
     *
     * @param name     the declared field name
     * @param accessor the field accessor
     */
    public void addFieldAccessor(final String name, final XdStorageObjectField accessor) {
        if (fieldAccesors == null) {
            fieldAccesors = new LinkedHashMap<>();
        }
        fieldAccesors.put(name, accessor);
    }

    /**
     * The accessors of the index's declared (non-primary) fields.
     *
     * @return the field-name-to-accessor map (never {@code null}; empty when none declared)
     */
    public Map<String, XdStorageObjectField> getFieldAccesors() {
        return fieldAccesors == null ? Collections.emptyMap() : fieldAccesors;
    }

    /**
     * Routes a nested child field to another declared index: objects read off this field
     * are indexed by the named index of their class.
     *
     * @param fieldName           this index's child field name
     * @param childClassIndexName the index name (of the child class) to index the child with
     */
    public void addChildIndex(final String fieldName, final String childClassIndexName) {
        if (childrenIndexes == null) {
            childrenIndexes = new HashMap<>();
        }
        childrenIndexes.put(fieldName, childClassIndexName);
    }

    /**
     * The child-field-to-child-index routing of this index.
     *
     * @return the child field name to child index name map (never {@code null}; empty when none declared)
     */
    public Map<String, String> getChildrenIndexes() {
        return childrenIndexes == null ? Collections.emptyMap() : childrenIndexes;
    }

    /**
     * Adds an accessor for one indexed field of a nested child entity class. A class may
     * appear under several parent fields; the accessors accumulate in declaration order.
     *
     * @param fieldName the parent's child field name
     * @param cl        the child entity's class
     * @param accessor  the child's field accessor
     */
    public void addChildAccessor(final String fieldName, final Class<?> cl, final XdStorageObjectField accessor) {
        if (childrenAccessors == null) {
            childrenAccessors = new LinkedHashMap<>();
        }

        List<XdStoragePair<String, XdStorageObjectField>> accessors = childrenAccessors.get(cl);
        if(accessors == null) {
            accessors = new ArrayList<>();
            childrenAccessors.put(cl, accessors);
        }

        accessors.add(new XdStoragePair<>(fieldName, accessor));
    }

    /**
     * The nested-child field accessors grouped by child class.
     *
     * @return the child-class-to-(field-name, accessor)-list view (never {@code null};
     *         empty when no children declared)
     */
    public Collection<List<XdStoragePair<String, XdStorageObjectField>>> getChildAccessors() {
        return childrenAccessors == null ? Collections.emptyList() : childrenAccessors.values();
    }

    /**
     * Declares the id field of a nested child entity class.
     *
     * @param cl       the child entity's class
     * @param idField  the child's id field accessor
     */
    public void addChildIdField(final Class<?> cl, final XdStorageObjectIdField idField) {
        if (childrenIdFields == null) {
            childrenIdFields = new LinkedHashMap<>();
        }
        childrenIdFields.put(cl, idField);
    }

    /**
     * The id field of a nested child entity class.
     *
     * @param cl the child entity's class
     * @return the child's id field accessor, or a dummy that always reads {@code null}
     *         when the class declares no id field
     */
    public XdStorageObjectIdField getChildIdField(final Class<?> cl) {
        return childrenIdFields == null ? XdStorageDummyObjectIdField.Instance : childrenIdFields.get(cl);
    }

    /**
     * Adds an accessor for one indexed field of a nested child entity class (the per-class
     * sibling of {@link #addFieldAccessor}). A class may appear under several parent fields.
     *
     * @param cl        the child entity's class
     * @param name      the child's declared field name
     * @param accessor  the child's field accessor
     */
    public void addChildFieldAccessor(final Class<?> cl, final String name, final XdStorageObjectField accessor) {
        if (childrenFieldAccessors == null) {
            childrenFieldAccessors = new LinkedHashMap<>();
        }

        Map<String, XdStorageObjectField> accessors = childrenFieldAccessors.get(cl);
        if (accessors == null) {
            childrenFieldAccessors.put(cl, accessors = new HashMap<>());
        }
        accessors.put(name, accessor);
    }

    /**
     * The indexed-field accessors of a nested child entity class.
     *
     * @param cl the child entity's class
     * @return the child's field-name-to-accessor map (never {@code null}; empty when the
     *         class declares no indexed fields)
     */
    public Map<String, XdStorageObjectField> getChildFieldAccesors(final Class<?> cl) {
        return childrenFieldAccessors == null ? Collections.emptyMap() : childrenFieldAccessors.get(cl);
    }
}
