package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as the back-reference to the OWNING parent object: the field's type is
 * the parent class, and the engine fills it automatically when the child is loaded —
 * application code must not set it. Pairs with the child being stored embedded
 * ({@code StoreWithParentObject}) or referenced from the parent.
 */
@Target(value = ElementType.FIELD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageParentObject {

}
