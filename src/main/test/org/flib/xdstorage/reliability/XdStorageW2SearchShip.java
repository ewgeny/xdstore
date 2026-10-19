package org.flib.xdstorage.reliability;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectIdIndexType;
import org.flib.xdstorage.annotations.XdStorageObjectChildSearchIndex;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.annotations.XdStorageObjectSearchIndex;
import org.flib.xdstorage.index.XdStorageIndexType;

import java.util.ArrayList;
import java.util.List;

/**
 * W-2: судно с BTree id index (t=8) и поисковым индексом (B+Tree внутри) —
 * совместно закрывают P0-кейс «B+Tree структуры нечитаемы после переоткрытия».
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 8)
@XdStorageObjectSearchIndex(indexName = "w2_ship_idx", indexFieldNames = {"name", "tonnage"}, t = 8, childrenIndexes = {
        @XdStorageObjectChildSearchIndex(childFieldName = "captain", childClassIndexName = "w2_crew_idx"),
        @XdStorageObjectChildSearchIndex(childFieldName = "crew", childClassIndexName = "w2_crew_idx"),
})
public class XdStorageW2SearchShip {
    @XdStorageObjectId
    private Long id;
    private String name;
    private int tonnage;
    private XdStorageW2SearchCrewMember captain;
    private List<XdStorageW2SearchCrewMember> crew;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTonnage() { return tonnage; }
    public void setTonnage(int tonnage) { this.tonnage = tonnage; }
    public XdStorageW2SearchCrewMember getCaptain() { return captain; }
    public void setCaptain(XdStorageW2SearchCrewMember captain) { this.captain = captain; }
    public List<XdStorageW2SearchCrewMember> getCrew() { return crew; }
    public void setCrew(List<XdStorageW2SearchCrewMember> crew) { this.crew = crew; }

    public void addCrewMember(XdStorageW2SearchCrewMember member) {
        if (crew == null) {
            crew = new ArrayList<>();
        }
        crew.add(member);
    }
}
