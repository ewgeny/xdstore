/**
 * Primary-key generation: the {@link io.github.xdstorage.idgeneration.IXdStorageIdGenerator}
 * SPI plus the engine's implementations — the persisted Hi-Lo counters for
 * {@code long}/{@code int} ids (transactional batch allocation with prefetch), UUID
 * strings, and the router ({@code XdStorageDefaultIdGenerator}) that maps an id field's
 * {@code XdStorageIdGeneratorType} to its generator. Application code meets this
 * package through {@code @XdStorageObjectFieldProperties}, not directly.
 */
package io.github.xdstorage.idgeneration;
