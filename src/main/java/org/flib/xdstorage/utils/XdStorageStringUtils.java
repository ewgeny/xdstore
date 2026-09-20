package org.flib.xdstorage.utils;

public final class XdStorageStringUtils {

    private XdStorageStringUtils() {
        // do nothing
    }

    public static boolean isBlank(final String str) {
        return str == null || str.trim().length() == 0;
    }
}
