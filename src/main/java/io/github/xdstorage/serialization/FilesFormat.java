package io.github.xdstorage.serialization;

/**
 * On-disk serialization formats.
 *
 * A2 (2.0.0 API freeze): the {@code JSON} constant was REMOVED — it silently wrote XML
 * content into {@code .json}-named files (the default factory is an XML implementation),
 * which is a data-honesty landmine for embedders. Real JSON support is a demand-driven
 * feature (MASTER-PLAN F-5/Step 4), not an enum constant that lies. A 1.x folder written
 * through the old constant is an XML-format folder with a {@code .json} extension —
 * see the migration recipe in docs/EMBEDDING.md, "Compatibility &amp; support statement".
 */
public enum FilesFormat {
    XML, YAML
}
