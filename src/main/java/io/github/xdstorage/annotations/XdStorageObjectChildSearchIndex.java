package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one child-class index that the parent's search index joins with (an element
 * of {@link XdStorageObjectSearchIndex#childrenIndexes()}): a query criterion can then
 * reach through the parent's child field into an index declared on the CHILD class,
 * using {@code IXdStorageChildCriterionOperator}.
 *
 * <p>Both names are required and must exist: {@code childFieldName} is the parent's
 * field holding the children; {@code childClassIndexName} is the {@code indexName} of a
 * {@code @XdStorageObjectSearchIndex} declared on the child class.
 */
@Target(value = ElementType.ANNOTATION_TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectChildSearchIndex {

/** Names the parent's child field.
     * @return the parent's field holding the children the criterion reaches through */
    String childFieldName();

/** Names the child class's joined index.
     * @return the {@code indexName} of a {@code @XdStorageObjectSearchIndex} declared on the CHILD class */
    String childClassIndexName();
}
