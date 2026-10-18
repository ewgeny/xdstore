package org.flib.xdstorage.search;

import org.flib.xdstorage.XdStoragePolicy;
import org.flib.xdstorage.annotations.XdStorageObjectId;
import org.flib.xdstorage.annotations.XdStorageObjectIdIndexType;
import org.flib.xdstorage.annotations.XdStorageObjectChildSearchIndex;
import org.flib.xdstorage.annotations.XdStorageObjectPolicy;
import org.flib.xdstorage.annotations.XdStorageObjectSearchIndex;
import org.flib.xdstorage.index.XdStorageIndexType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Тестовая сущность поискового движка: корабль с дочерними объектами во ВСЕХ формах
 * контейнеров (одиночный объект, коллекция, массив, карта).
 * ВАЖНО (R-49): класс ОБЯЗАН быть верхнего уровня - рантайм-генератор оберток СУБД
 * пишет binary-name вложенного класса (Outer$Inner), который javac JDK 9+ в исходнике
 * именованного пакета не разрешает; генерация оберток для вложенных сущностей
 * деградирует в dummy, а чтение объектов падает.
 */
@XdStorageObjectPolicy(policy = XdStoragePolicy.StoreAsClassObjects)
@XdStorageObjectIdIndexType(indexType = XdStorageIndexType.BTree, t = 4)
@XdStorageObjectSearchIndex(indexName = "ship_idx", indexFieldNames = {"name", "tonnage"}, t = 8, childrenIndexes = {
        @XdStorageObjectChildSearchIndex(childFieldName = "captain", childClassIndexName = "crew_idx"),
        @XdStorageObjectChildSearchIndex(childFieldName = "crew", childClassIndexName = "crew_idx"),
        @XdStorageObjectChildSearchIndex(childFieldName = "passengers", childClassIndexName = "crew_idx"),
        @XdStorageObjectChildSearchIndex(childFieldName = "visitors", childClassIndexName = "crew_idx"),
})
public class SearchEngineShip {
    @XdStorageObjectId
    private Long id;
    private String name;
    private int tonnage;
    private SearchEngineCrewMember captain;
    private List<SearchEngineCrewMember> crew;
    private SearchEngineCrewMember[] passengers;
    private Map<String, SearchEngineCrewMember> visitors;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getTonnage() { return tonnage; }
    public void setTonnage(int tonnage) { this.tonnage = tonnage; }
    public SearchEngineCrewMember getCaptain() { return captain; }
    public void setCaptain(SearchEngineCrewMember captain) { this.captain = captain; }
    public List<SearchEngineCrewMember> getCrew() { return crew; }
    public void setCrew(List<SearchEngineCrewMember> crew) { this.crew = crew; }
    public SearchEngineCrewMember[] getPassengers() { return passengers; }
    public void setPassengers(SearchEngineCrewMember[] passengers) { this.passengers = passengers; }
    public Map<String, SearchEngineCrewMember> getVisitors() { return visitors; }
    public void setVisitors(Map<String, SearchEngineCrewMember> visitors) { this.visitors = visitors; }

    public void addCrewMember(SearchEngineCrewMember member) {
        if (crew == null) {
            crew = new ArrayList<>();
        }
        crew.add(member);
    }
}
