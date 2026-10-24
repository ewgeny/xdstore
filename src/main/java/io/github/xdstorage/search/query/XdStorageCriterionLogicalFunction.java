package io.github.xdstorage.search.query;

/** The logical function joining two adjacent sub-criteria of a
 * {@link XdStorageComplexCriterion}. */
public enum XdStorageCriterionLogicalFunction {

    /** Conjunctive join: the chain result is false unless this sub-criterion passes. */
    AND,
    /** Disjunctive join: the chain result is true once this sub-criterion passes. */
    OR;
}
