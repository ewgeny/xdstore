/**
 * The public API of XdStorage — the embedder's surface. Everything an application
 * touches lives here: the {@link io.github.xdstorage.IXdStorage}/{@link io.github.xdstorage.IXdFileStorage}
 * contract, the {@link io.github.xdstorage.XdStorageProvider} factory (folder lock,
 * name registry, integrity verify), open-time {@link io.github.xdstorage.XdStorageConfig},
 * and the {@link io.github.xdstorage.XdStoragePolicy} storage policies.
 *
 * <p>Contract rule of thumb: CRUD needs a transaction (bound to the calling thread or
 * passed explicitly); failures throw but never roll back silently; reads answer from
 * the transaction's snapshot. The full operational contract — lifecycle, threading,
 * durability, codegen requirements — is {@code docs/EMBEDDING.md}; the guided tour is
 * {@code README.md} + {@code docs/GUIDE.md}. Every other {@code io.github.xdstorage.*}
 * package is engine machinery and may change between releases.
 */
package io.github.xdstorage;
