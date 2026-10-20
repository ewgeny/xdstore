package org.flib.xdstorage.utils;

import org.flib.xdstorage.XdStorageProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A2: the runtime-visible library version. Under the Maven build the filtered
 * /xdstorage.properties resource is on the classpath, so the version must be a semver
 * string — never the "unknown" fallback (that would mean resource filtering broke).
 */
class XdStorageVersionTest {

    @Test
    void versionIsSemverUnderMavenBuild() {
        final String version = XdStorageVersion.getVersion();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"),
                "filtered xdstorage.properties must provide a semver version, got: " + version);
    }

    @Test
    void providerExposesTheSameVersion() {
        assertEquals(XdStorageVersion.getVersion(), XdStorageProvider.getVersion());
    }
}
