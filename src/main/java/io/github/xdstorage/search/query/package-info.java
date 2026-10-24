/**
 * The search-query model behind {@code IXdFileStorage.load(cl, indexName, query)}:
 * criteria ({@link io.github.xdstorage.search.query.XdStoragePrimaryCriterion} against
 * the index key, {@link io.github.xdstorage.search.query.XdStorageCriterion} against
 * any indexed field, child/complex/not composites) and the
 * {@link io.github.xdstorage.search.query.IXdStorageCriterionOperator} set with its
 * pinned semantics — inclusive BETWEEN ({@link io.github.xdstorage.search.query.XdStorageRange}),
 * nulls never satisfy range operators, mixed types fail loudly — plus the OR-of-pairs
 * {@link io.github.xdstorage.search.query.XdStorageSearchQuery} with ORDER BY / LIMIT /
 * OFFSET shaping.
 */
package io.github.xdstorage.search.query;
