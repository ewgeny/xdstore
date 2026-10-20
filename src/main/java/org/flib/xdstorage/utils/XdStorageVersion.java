package org.flib.xdstorage.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A2: the library version, surfaced at runtime via {@code XdStorage#getVersion()} and
 * {@code XdStorageProvider#getVersion()}. Single source of truth is the pom
 * {@code <version>}; it reaches the jar as the Maven-filtered {@code /xdstorage.properties}
 * resource. "unknown" is returned when the resource is absent or unfiltered (a classpath
 * assembled without Maven) — a broken version resource must never break opening a storage.
 */
public final class XdStorageVersion {

    private static final String RESOURCE = "/xdstorage.properties";

    private static final Object LOCK = new Object();

    private static volatile String cached;

    private XdStorageVersion() {
    }

    public static String getVersion() {
        String v = cached;
        if (v == null) {
            synchronized (LOCK) {
                v = cached;
                if (v == null) {
                    final Properties props = new Properties();
                    try (final InputStream in = XdStorageVersion.class.getResourceAsStream(RESOURCE)) {
                        if (in != null) {
                            props.load(in);
                        }
                    } catch (final IOException e) {
                        // fall through to "unknown"
                    }
                    v = props.getProperty("version", "unknown");
                    cached = v;
                }
            }
        }
        return v;
    }
}
