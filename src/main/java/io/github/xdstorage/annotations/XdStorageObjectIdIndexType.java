package io.github.xdstorage.annotations;

import io.github.xdstorage.index.XdStorageIndexType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = ElementType.TYPE)
@Retention(value = RetentionPolicy.RUNTIME)
public @interface XdStorageObjectIdIndexType {

    XdStorageIndexType indexType() default XdStorageIndexType.Hash;

    /**
     * B+Tree fill factor (node holds t-1 … 2t-1 keys; one node = one file on disk).
     * Default 64 — the measured optimum of the config sweep (docs/PERFORMANCE-RECOMMENDATIONS.md §4):
     * best bulk insert, commit latency, delete and cold-read costs at small-to-medium N.
     * The pre-study default 500 optimizes file count at the price of ~12× slower inserts;
     * values below 8 are pathological on every axis.
     */
    int t() default 64;
}
