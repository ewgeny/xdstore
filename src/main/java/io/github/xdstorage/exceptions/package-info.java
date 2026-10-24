/**
 * The exception hierarchy: two CHECKED families thrown by the CRUD contract —
 * {@link io.github.xdstorage.exceptions.XdStorageException} (data/contract failure:
 * catch to react to your data) and {@link io.github.xdstorage.exceptions.XdStorageConnectionException}
 * (I/O failure: catch to react to the environment) — and the UNCHECKED
 * {@link io.github.xdstorage.exceptions.XdStorageRuntimeException} for hard contract
 * violations (shut-down storage, format refusal, malformed query).
 *
 * <p>{@link io.github.xdstorage.exceptions.XdStorageReferenceInvisibleException} extends
 * the checked exception but means something softer: a reference invisible in the
 * reader's MVCC snapshot — normal concurrent behavior, not damage.
 */
package io.github.xdstorage.exceptions;
