package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AUTOSAVE (docs/tasks/2.1.0/22): marks a parent's child-object field for cascade-save —
 * when the parent is saved or updated, the annotated children living under independent
 * policies ({@code StoreAsClassObjects}, {@code StoreAsSingleObject}) are cascade-upserted
 * in the same transaction, with triggers, observers, id generation and rollback semantics
 * identical to explicit saves.
 *
 * <p>V1 semantics (the full contract in README § Defining the data model):
 * <ul>
 *   <li>child id {@code null} → insert (a generated id backfills the SAME instance);</li>
 *   <li>child id set → update if it exists, insert otherwise;</li>
 *   <li>the child's own {@code @XdStorageAutoSave} fields cascade further — cycles are safe
 *       (each object is processed once per operation);</li>
 *   <li>children REMOVED from the collection are NOT deleted — they keep their independent
 *       resources (cascade-delete is deliberately out of scope);</li>
 *   <li>an element class under {@code StoreWithParentObject} is a no-op with a metadata
 *       WARN (the parent's write already embeds it).</li>
 * </ul>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface XdStorageAutoSave {

}
