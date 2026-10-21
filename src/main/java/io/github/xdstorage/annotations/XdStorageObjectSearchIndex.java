package io.github.xdstorage.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectSearchIndex {

    String indexName();

    String[] indexFieldNames();

    /**
     * B+Tree fill factor for the two per-index trees (main value tree + key tree).
     * Default 64 — the measured optimum of the config sweep (docs/PERFORMANCE-RECOMMENDATIONS.md §7):
     * best indexed-insert throughput and cold-search latency. The pre-study default 500 is ~5×
     * slower on indexed inserts and pays tens of ms on the first cold query.
     */
    int t() default 64;

    XdStorageObjectChildSearchIndex[] childrenIndexes() default {};
}
