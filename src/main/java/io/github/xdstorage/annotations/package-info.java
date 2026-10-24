/**
 * The entity-mapping annotations: how application classes become stored entities.
 * {@code @XdStorageObjectId} marks the primary key (mandatory on every stored class);
 * {@code @XdStorageObjectPolicy} chooses where records live (embedded / one-file-per-object /
 * class-batched); {@code @XdStorageObjectSearchIndex} + {@code @XdStorageObjectChildSearchIndex}
 * declare the indexes behind query loads; {@code @XdStorageObjectIdIndexType} picks the
 * id index structure; {@code @XdStorageObjectFieldProperties} tunes fields and id
 * generation; {@code @XdStorageParentObject} and {@code @XdStorageLoadByGetMethod}
 * control graph reconstruction; {@code @XdStorageAutoSave} cascades child saves.
 *
 * <p>Annotations are read at runtime (RUNTIME retention) by the class-metadata scanner;
 * they carry no behavior of their own.
 */
package io.github.xdstorage.annotations;
