package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class XdStorageComplexCriterion implements IXdStorageCriterion {

    private List<IXdStorageCriterion> criterions = new ArrayList<>();

    private List<XdStorageCriterionLogicalFunction> functions = new ArrayList<>();

    public XdStorageComplexCriterion() {

    }

    public XdStorageComplexCriterion(final IXdStorageCriterion criterion) {
        criterions.add(criterion);
    }

    public XdStorageComplexCriterion and(final IXdStorageCriterion criterion) {
        if (criterions.size() > 0) {
            functions.add(XdStorageCriterionLogicalFunction.AND);
        }
        criterions.add(criterion);

        return this;
    }

    public XdStorageComplexCriterion or(final IXdStorageCriterion criterion) {
        if (criterions.size() > 0) {
            functions.add(XdStorageCriterionLogicalFunction.OR);
        }
        criterions.add(criterion);

        return this;
    }

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
