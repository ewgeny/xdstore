package io.github.xdstorage.annotations;

import io.github.xdstorage.XdStoragePolicy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the physical storage policy of the entity class
 * ({@link io.github.xdstorage.XdStoragePolicy}); the default is
 * {@link io.github.xdstorage.XdStoragePolicy#StoreWithParentObject} (embedded — the
 * object is stored inside its parent's record). Classes stored on their own resource
 * declare {@code StoreAsSingleObject} or {@code StoreAsClassObjects}.
 */
@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectPolicy {

/** Selects the storage policy.
     * @return where the engine stores the class's objects (default: embedded in the parent) */
    XdStoragePolicy policy() default XdStoragePolicy.StoreWithParentObject;
}
