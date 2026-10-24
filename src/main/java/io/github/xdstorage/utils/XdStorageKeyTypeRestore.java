package io.github.xdstorage.utils;

import io.github.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;

/**
 * BUG-C/BUG-E shared conversion: restores a record/tree key to the DECLARED field type
 * after the YAML round-trip (the generic reader binds scalars to declared types, and an
 * {@code Object}-declared key field comes back as a raw String). Values already of the
 * target type pass through; strings/numbers/booleans are parsed via the engine's
 * simple-type conversion; anything unparseable is returned as-is so the caller's lookup
 * misses loudly instead of poisoning the structure with a wrongly-typed key.
 *
 * <p>The authority is the DECLARED field type ({@code Field#getType()}): the field-info
 * {@code valueClass} is {@code Object.class} for id fields and would make every key
 * "already the right type", silently restoring nothing (the BUG-C lesson).
 */
public final class XdStorageKeyTypeRestore {

    private XdStorageKeyTypeRestore() {
    }

    /**
     * BUG-E: the search-node entry restorer as one hook — the btree layer stays
     * search-agnostic (it hands every key/record entry through this), while the search
     * layer owns the type authority: the tree KEY's value is the indexed class's PRIMARY
     * index field, the object pointer / record id is the entity's id field, and the index
     * RECORD's primaryIndexValue is what the criterion layer compares.
     *
     * @param keyValueType the declared type of the tree KEY's value (the indexed class's primary
     *                     index field type)
     * @param objectIdType the declared type of the entity id carried as the object pointer
     * @return a restorer applicable to every entry of a search btree; unknown entry kinds pass through unchanged
     */
    public static java.util.function.UnaryOperator<Object> searchEntryRestorer(
            final Class<?> keyValueType, final Class<?> objectIdType) {
        return nodeEntry -> {
            if (nodeEntry instanceof io.github.xdstorage.search.key.XdStorageSearchIndexKey) {
                final io.github.xdstorage.search.key.XdStorageSearchIndexKey searchKey =
                        (io.github.xdstorage.search.key.XdStorageSearchIndexKey) nodeEntry;
                searchKey.setValue(restore(searchKey.getValue(), keyValueType));
                searchKey.setObjectId(restore(searchKey.getObjectId(), objectIdType));
            } else if (nodeEntry instanceof io.github.xdstorage.search.data.XdStorageSearchIndexRecord) {
                final io.github.xdstorage.search.data.XdStorageSearchIndexRecord record =
                        (io.github.xdstorage.search.data.XdStorageSearchIndexRecord) nodeEntry;
                record.setPrimaryIndexValue(restore(record.getPrimaryIndexValue(), keyValueType));
                record.setId(restore(record.getId(), objectIdType));
            } else if (nodeEntry instanceof io.github.xdstorage.search.data.XdStorageSearchKeyIndexRecord) {
                // the ADDRESSING tree (key_<index>) entries: id = the entity id, value = the primary
                final io.github.xdstorage.search.data.XdStorageSearchKeyIndexRecord record =
                        (io.github.xdstorage.search.data.XdStorageSearchKeyIndexRecord) nodeEntry;
                record.setObjectId(restore(record.getObjectId(), objectIdType));
                record.setValue(restore(record.getValue(), keyValueType));
            }
            return nodeEntry;
        };
    }

    /**
     * Restores one key to the declared field type after the YAML round-trip: values already of
     * the target type (primitives widened to their wrappers) pass through; strings, numbers and
     * booleans are converted via the engine's simple-type conversion; anything unparseable is
     * returned unchanged so the caller's lookup misses instead of storing a wrongly-typed key.
     *
     * @param key the value as read back from disk, may be {@code null}
     * @param declaredType the declared field type (primitive types are treated as their wrappers), may be {@code null}
     * @return the value coerced to {@code declaredType}, or the original value when no conversion applies
     */
    public static Object restore(final Object key, final Class<?> declaredType) {
        if (key == null || declaredType == null) {
            return key;
        }
        Class<?> target = declaredType;
        if (target == long.class) target = Long.class;
        if (target == int.class) target = Integer.class;
        if (target == double.class) target = Double.class;
        if (target == float.class) target = Float.class;
        if (target == boolean.class) target = Boolean.class;
        if (target == char.class) target = Character.class;
        if (target.isInstance(key)) {
            return key;
        }
        if (key instanceof String || key instanceof Number || key instanceof Boolean) {
            try {
                return new XdStorageDefaultSimpleTypeHelper().simpleTypeFromString(target, key.toString());
            } catch (final RuntimeException e) {
                return key;
            }
        }
        return key;
    }
}
