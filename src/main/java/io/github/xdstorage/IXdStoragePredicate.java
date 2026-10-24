package io.github.xdstorage;

/** A filter callback for {@code IXdFileStorage.load(cl, predicate, ...)}: runs per
 * materialized record inside the read (an in-snapshot scan — no index involved).
 *
 * @param <T> the entity class being filtered */
public interface IXdStoragePredicate<T> {

    /**
     * Tests the record against the filter.
     *
     * @param object the record being filtered
     * @return {@code true} to include the object in the result
     */
    boolean passed(T object);

}
