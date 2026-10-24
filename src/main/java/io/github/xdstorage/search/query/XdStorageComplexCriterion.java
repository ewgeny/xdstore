package io.github.xdstorage.search.query;

import io.github.xdstorage.search.data.XdStorageSearchIndexRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A composite criterion: sub-criteria combined left-to-right with the appended
 * logical functions ({@code and()}/{@code or()}), evaluated with short-circuit
 * semantics — an AND chain stops at the first failure, an OR at the first success.
 * Thread-safe for concurrent evaluations after construction (the builder methods are
 * not thread-safe). */
public class XdStorageComplexCriterion implements IXdStorageCriterion {

    private List<IXdStorageCriterion> criterions = new ArrayList<>();

    private List<XdStorageCriterionLogicalFunction> functions = new ArrayList<>();

    /** An empty composite — passes every record until a sub-criterion is appended. */
    public XdStorageComplexCriterion() {

    }

    /**
     * A composite with one sub-criterion (the first, with no joining function).
     *
     * @param criterion the first sub-criterion
     */
    public XdStorageComplexCriterion(final IXdStorageCriterion criterion) {
        criterions.add(criterion);
    }

    /** Appends a sub-criterion conjunctively (AND with everything before it).
     *
     * @param criterion the sub-criterion to append
     * @return this composite, for call chaining
     */
    public XdStorageComplexCriterion and(final IXdStorageCriterion criterion) {
        if (criterions.size() > 0) {
            functions.add(XdStorageCriterionLogicalFunction.AND);
        }
        criterions.add(criterion);

        return this;
    }

    /** Appends a sub-criterion disjunctively (OR with everything before it).
     *
     * @param criterion the sub-criterion to append
     * @return this composite, for call chaining
     */
    public XdStorageComplexCriterion or(final IXdStorageCriterion criterion) {
        if (criterions.size() > 0) {
            functions.add(XdStorageCriterionLogicalFunction.OR);
        }
        criterions.add(criterion);

        return this;
    }

    /** Evaluates the chain left-to-right with short-circuit AND/OR semantics;
     * an empty composite passes everything. */
    @Override
    public boolean passed(final XdStorageSearchIndexRecord record) {
        boolean result;

        if (criterions.isEmpty()) {
            result = true;
        } else {
            final Iterator<IXdStorageCriterion> it = criterions.iterator();
            final Iterator<XdStorageCriterionLogicalFunction> funIt = functions.iterator();

            result = it.next().passed(record);
            while (funIt.hasNext() && it.hasNext()) {
                final XdStorageCriterionLogicalFunction fun = funIt.next();

                if (result) {
                    if (fun == XdStorageCriterionLogicalFunction.OR) {
                        continue;
                    } else {
                        result = it.next().passed(record);
                    }
                } else {
                    if (fun == XdStorageCriterionLogicalFunction.AND) {
                        break;
                    } else {
                        result = it.next().passed(record);
                    }
                }
            }
        }

        return result;
    }
}
