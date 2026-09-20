package org.flib.xdstorage.sqlstorage.sql.processor;

public abstract class XdStorageAbstractSQLCommand implements IXdStorageSQLCommand {

    private Integer priority;

    protected XdStorageAbstractSQLCommand(final Integer priority) {
        this.priority = priority;
    }

    public Integer getPriority() {
        return priority;
    }

    @Override
    public int compareTo(final XdStorageAbstractSQLCommand o) {
        return priority.compareTo(o.priority);
    }
}
