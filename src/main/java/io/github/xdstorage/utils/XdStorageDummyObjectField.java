package io.github.xdstorage.utils;

/**
 * A no-op {@link XdStorageObjectField} placeholder for classes without real persistence fields:
 * reads answer {@code null} and writes are discarded. The shared immutable {@link #Instance}
 * makes it stateless and safe to use from any thread. Engine-internal — not part of the public API.
 */
public class XdStorageDummyObjectField extends XdStorageObjectField {

    /** The shared dummy instance, exposed through the base field type. */
    public static final XdStorageObjectField Instance = new XdStorageDummyObjectField();

    private XdStorageDummyObjectField() {
        super(null, null, null, null);
    }

    /** Always answers {@code null}: the dummy carries no backing object.
     *
     * @param obj ignored
     * @param <T> the value type
     * @return {@code null}, always
     */
    @Override
    public <T> T get(Object obj) {
        return null;
    }

    /** Discards the value: the dummy has nothing to write to.
     *
     * @param obj ignored
     * @param value ignored
     * @param <T> the value type
     */
    @Override
    public <T> void set(Object obj, T value) {
        // do nothing
    }
}
