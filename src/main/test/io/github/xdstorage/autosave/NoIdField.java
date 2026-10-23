package io.github.xdstorage.autosave;

import io.github.xdstorage.XdStoragePolicy;
import io.github.xdstorage.annotations.XdStorageObjectPolicy;

/** a class deliberately missing @XdStorageObjectId — save() must reject it loudly */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
public class NoIdField {
    private String whatever;
    public String getWhatever() { return whatever; }
    public void setWhatever(String whatever) { this.whatever = whatever; }
}
