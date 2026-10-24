package io.github.xdstorage.structure.update;

import io.github.xdstorage.annotations.XdStorageObjectId;

import java.util.Date;

/**
 * The journal record of one executed structure update (migration) script: the script's
 * unique name (the record id), the execution result and the time it ran. Persisted by the
 * structure manager after each successful script execution; before executing a script the
 * manager reads this journal and skips scripts already recorded as successful, while failed
 * ones are re-run on the next structure check.
 *
 * <p>Instances are created and persisted only by the structure manager. Engine-internal —
 * not part of the public API.</p>
 */
public class XdStorageUpdateStructureRecord {

    @XdStorageObjectId
    private String name;

    private Date time;

    private XdStorageUpdateStructureResult result;

    /**
     * Creates an empty journal record.
     */
    public XdStorageUpdateStructureRecord() {
    }

    /**
     * Returns the name of the executed update script.
     *
     * @return the unique name of the executed update script
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the executed update script.
     *
     * @param name the unique name of the executed update script
     */
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns when the script finished executing.
     *
     * @return the time the script finished executing, or {@code null} when not recorded
     */
    public Date getTime() {
        return time;
    }

    /**
     * Sets when the script finished executing.
     *
     * @param time the time the script finished executing
     */
    public void setTime(final Date time) {
        this.time = time;
    }

    /**
     * Returns the journalled outcome of the script execution.
     *
     * @return the execution outcome of the script, or {@code null} when not recorded
     */
    public XdStorageUpdateStructureResult getResult() {
        return result;
    }

    /**
     * Sets the journalled outcome of the script execution.
     *
     * @param result the execution outcome of the script
     */
    public void setResult(final XdStorageUpdateStructureResult result) {
        this.result = result;
    }
}
