package io.github.xdstorage.serialization;

/**
 * Serialization SPI: pairs an {@link IXdStorageObjectsReader} with an
 * {@link IXdStorageObjectsWriter} for one on-disk {@link FilesFormat}. This is the
 * extension point that lets a storage plug its own codec (the engine ships the XML
 * {@code XdStorageDefaultIOFactory} and the YAML {@code XdStorageYamlIOFactory}).
 *
 * <p>Implementations must be thread-safe: one factory instance is shared by all worker
 * threads of a storage, while the produced readers/writers are typically confined to
 * the calling thread. Engine-internal — not part of the public API.</p>
 */
public interface IXdStorageIOFactory {

    /**
     * Returns the reader used to demarshal resource files of this format.
     *
     * @return an {@link IXdStorageObjectsReader} for this factory's format
     */
    IXdStorageObjectsReader newInstanceReader();

    /**
     * Returns the writer used to marshal resource files of this format.
     *
     * @return an {@link IXdStorageObjectsWriter} for this factory's format
     */
    IXdStorageObjectsWriter newInstanceWriter();
}
