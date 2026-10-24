package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a child-collection field for LAZY loading through the generated wrapper's
 * getter: the first getter call loads the collection from the storage
 * ({@code storage.load(getter result, transaction)}) and caches it in the object.
 *
 * <p>Contract notes (docs/GUIDE.md &sect; References between objects):
 * <ul>
 *   <li>the first access must happen inside a transaction — the getter resolves the
 *       field through the thread-bound transaction;</li>
 *   <li>a failed lazy load marks the transaction rollback-only and throws
 *       {@code XdStorageRuntimeException};</li>
 *   <li>requires the runtime codegen wrappers (a JDK at runtime; a JRE degrades to
 *       dummy wrappers with no lazy loading).</li>
 * </ul>
 */
@Target(value = ElementType.FIELD)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageLoadByGetMethod {

}
