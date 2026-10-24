package io.github.xdstorage.utils;

/**
 * A no-op {@link XdStorageObjectIdField} placeholder for classes without an id field: reads
 * answer {@code null} and writes are discarded. The shared immutable {@link #Instance} makes it
 * stateless and safe to use from any thread. Engine-internal — not part of the public API.
 */
public class XdStorageDummyObjectIdField extends XdStorageObjectIdField {

    /** The shared dummy instance. */
    public static final XdStorageObjectIdField Instance = new XdStorageDummyObjectIdField();

    private XdStorageDummyObjectIdField() {
        super(null, null, null, null);
    }

    /** Always answers {@code null}: the dummy carries no id.
     *
     * @param object ignored
     * @return {@code null}, always
     */
    @Override
    public Object get(final Object object) {
        return null;
    }

    /** Discards the id: the dummy has nothing to write to.
     *
     * @param object ignored
     * @param objectId ignored
     */
    @Override
    public void set(final Object object, final Object objectId) {
        // do nothing
    }
}
