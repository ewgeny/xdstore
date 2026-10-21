package io.github.xdstorage.serialization.yaml;

import io.github.xdstorage.entities.XdBlackHole;
import io.github.xdstorage.entities.XdGalaxy;
import io.github.xdstorage.entities.XdPlanet;
import io.github.xdstorage.entities.XdSatellite;
import io.github.xdstorage.entities.XdStar;
import io.github.xdstorage.entities.XdStarSystem;
import io.github.xdstorage.entities.XdUniverse;
import io.github.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import io.github.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import io.github.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Прямые round-trip тесты YAML-движка маршаллинга на модели тестовых сущностей.
 * Изолируют дефекты сериализации от транзакционного слоя хранилища:
 * каждый тест строит граф объектов, прогоняет writeObjects -> readObjects
 * и сверяет поле в поле.
 */
@DisplayName("YAML-движок: прямой round-trip на тестовых сущностях")
public class YamlEngineEntitiesRoundTripTest {

    private XdStorageYamlObjectsWriter writer;
    private XdStorageYamlObjectsReader reader;

    @BeforeEach
    public void setUp() {
        final IXdStorageSimpleTypeHelper helper = new XdStorageDefaultSimpleTypeHelper();
        final XdStorageServicesLocator services = mock(XdStorageServicesLocator.class);
        final io.github.xdstorage.idgeneration.IXdStorageIdGenerator idGenerator =
                mock(io.github.xdstorage.idgeneration.IXdStorageIdGenerator.class);
        writer = new XdStorageYamlObjectsWriter(services, helper, idGenerator);
        reader = new XdStorageYamlObjectsReader(helper);
    }

    private Collection<Object> roundTrip(final Object... objects) throws Exception {
        final StringWriter out = new StringWriter();
        writer.writeObjects(out, java.util.Arrays.asList(objects));
        return new ArrayList<>(reader.read(new StringReader(out.toString())));
    }

    // =========================================================================
    // 1. Встроенные объекты (StoreWithParentObject) и скаляры
    // =========================================================================

    @Test
    @DisplayName("XdStarSystem: встроенные stars, примитивы и строки переживают round-trip")
    public void starSystemEmbeddedStarsRoundTrip() throws Exception {
        final XdStarSystem system = new XdStarSystem();
        system.setId("sys-1");
        system.setNewName("StarSystem_Alpha");
        system.setPrimitive(42);

        final XdStar star1 = new XdStar();
        star1.setId("star-1");
        star1.setName("Star_A");
        star1.setParent(system);
        final XdStar star2 = new XdStar();
        star2.setId("star-2");
        star2.setName("Star_B");
        star2.setParent(system);
        system.setStars(new LinkedList<>(java.util.Arrays.asList(star1, star2)));
        system.setPlanets(new HashSet<>());

        final Collection<Object> parsed = roundTrip(system);
        assertEquals(1, parsed.size());
        final XdStarSystem restored = (XdStarSystem) parsed.iterator().next();

        assertEquals("sys-1", restored.getId());
        assertEquals("StarSystem_Alpha", restored.getNewName());
        assertEquals(42, restored.getPrimitive());
        assertNotNull(restored.getStars(), "stars collection lost");
        assertEquals(2, restored.getStars().size(), "stars size mismatch");

        final Map<String, XdStar> byId = new HashMap<>();
        for (final XdStar s : restored.getStars()) {
            assertNotNull(s, "null star in restored collection");
            byId.put(s.getId(), s);
        }
        final XdStar ra = byId.get("star-1");
        final XdStar rb = byId.get("star-2");
        assertNotNull(ra, "star-1 lost");
        assertNotNull(rb, "star-2 lost");
        assertEquals("Star_A", ra.getName());
        assertEquals("Star_B", rb.getName());
    }

    @Test
    @DisplayName("XdBlackHole: long и boolean скаляры переживают round-trip")
    public void blackHoleScalarsRoundTrip() throws Exception {
        final XdBlackHole hole = new XdBlackHole();
        hole.setId("hole-1");
        hole.setMass(123456789L);
        hole.setIsEventHorizonActive(true);

        final Collection<Object> parsed = roundTrip(hole);
        assertEquals(1, parsed.size());
        final XdBlackHole restored = (XdBlackHole) parsed.iterator().next();
        assertEquals("hole-1", restored.getId());
        assertEquals(123456789L, restored.getMass());
        assertTrue(restored.getIsEventHorizonActive());
    }

    // =========================================================================
    // 2. Полный граф XdPlanet: скаляры, Date, вложенный объект, массив, коллекция, map
    // =========================================================================

    @Test
    @DisplayName("XdPlanet: полный граф (Date, satellite, массив, коллекция, map) переживает round-trip")
    public void planetFullGraphRoundTrip() throws Exception {
        final XdPlanet planet = new XdPlanet();
        planet.setId(810L);
        planet.setStrId("str-810");
        planet.setName("Planet_Full");
        planet.setWaterPercent(77);
        final Date date = new Date(1700000000000L);
        planet.setDate(date);

        final XdSatellite satellite = new XdSatellite();
        satellite.setId(900);
        satellite.setName("Sat_Main");
        planet.setSatellite(satellite);

        final XdSatellite arrSat = new XdSatellite();
        arrSat.setId(901);
        arrSat.setName("Sat_Arr");
        planet.setArrSatellites(new XdSatellite[]{arrSat});

        final XdSatellite colSat = new XdSatellite();
        colSat.setId(902);
        colSat.setName("Sat_Col");
        planet.setColSatellites(new ArrayList<>(Collections.singletonList(colSat)));

        final XdSatellite mapSat = new XdSatellite();
        mapSat.setId(903);
        mapSat.setName("Sat_Map");
        final Map<String, XdSatellite> map = new LinkedHashMap<>();
        map.put("key-1", mapSat);
        planet.setMapSatellites(map);

        final Collection<Object> parsed = roundTrip(planet);
        assertEquals(1, parsed.size());
        final XdPlanet restored = (XdPlanet) parsed.iterator().next();

        assertEquals(810L, restored.getId());
        assertEquals("str-810", restored.getStrId());
        assertEquals("Planet_Full", restored.getName());
        assertEquals(77, restored.getWaterPercent());
        assertNotNull(restored.getDate(), "date lost");
        assertEquals(date.getTime(), restored.getDate().getTime());

        assertNotNull(restored.getSatellite(), "satellite lost");
        assertEquals("Sat_Main", restored.getSatellite().getName());

        assertNotNull(restored.getArrSatellites(), "arrSatellites lost");
        assertEquals(1, restored.getArrSatellites().length);
        assertEquals("Sat_Arr", restored.getArrSatellites()[0].getName());

        assertNotNull(restored.getColSatellites(), "colSatellites lost");
        assertEquals(1, restored.getColSatellites().size());
        assertEquals("Sat_Col", restored.getColSatellites().iterator().next().getName());

        assertNotNull(restored.getMapSatellites(), "mapSatellites lost");
        assertEquals(1, restored.getMapSatellites().size());
        assertEquals("Sat_Map", restored.getMapSatellites().get("key-1").getName());
    }

    // =========================================================================
    // 3. Ссылочные коллекции (главный подозреваемый: universe.galaxies)
    // =========================================================================

    @Test
    @DisplayName("XdUniverse: коллекция ссылок на галактики обязана переживать round-trip без NULL")
    public void universeGalaxyReferencesRoundTrip() throws Exception {
        final XdUniverse universe = new XdUniverse();
        universe.setId("u-1");
        final List<XdGalaxy> galaxies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final XdGalaxy galaxy = new XdGalaxy();
            galaxy.setId("galaxy-" + i);
            galaxies.add(galaxy);
        }
        universe.setGalaxies(galaxies);

        final Collection<Object> parsed = roundTrip(universe);
        assertEquals(1, parsed.size());
        final XdUniverse restored = (XdUniverse) parsed.iterator().next();

        assertNotNull(restored.getGalaxies(), "galaxies collection lost");
        assertEquals(3, restored.getGalaxies().size(), "galaxies size mismatch");
        final List<String> ids = new ArrayList<>();
        for (final XdGalaxy g : restored.getGalaxies()) {
            assertNotNull(g, "NULL galaxy in restored collection — references lost!");
            ids.add(g.getId());
        }
        assertTrue(ids.contains("galaxy-0"), "galaxy-0 lost, got " + ids);
        assertTrue(ids.contains("galaxy-1"), "galaxy-1 lost, got " + ids);
        assertTrue(ids.contains("galaxy-2"), "galaxy-2 lost, got " + ids);
    }

    @Test
    @DisplayName("XdGalaxy: hole (встроенный) и systems (ссылки) переживают round-trip")
    public void galaxyHoleAndSystemsRoundTrip() throws Exception {
        final XdGalaxy galaxy = new XdGalaxy();
        galaxy.setId("galaxy-main");

        final XdBlackHole hole = new XdBlackHole();
        hole.setId("hole-embedded");
        hole.setMass(555L);
        galaxy.setHole(hole);

        final List<XdStarSystem> systems = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            final XdStarSystem system = new XdStarSystem();
            system.setId("sys-ref-" + i);
            systems.add(system);
        }
        galaxy.setSystems(systems);

        final Collection<Object> parsed = roundTrip(galaxy);
        assertEquals(1, parsed.size());
        final XdGalaxy restored = (XdGalaxy) parsed.iterator().next();

        assertEquals("galaxy-main", restored.getId());
        assertNotNull(restored.getHole(), "hole lost");
        assertEquals("hole-embedded", restored.getHole().getId());
        assertEquals(555L, restored.getHole().getMass());
        assertNotNull(restored.getSystems(), "systems lost");
        assertEquals(2, restored.getSystems().size());
        for (final XdStarSystem s : restored.getSystems()) {
            assertNotNull(s, "NULL system in restored collection");
        }
    }

    @Test
    @DisplayName("XStarSystem: ссылочные planets (HashSet) переживают round-trip без NULL-дедупликации")
    public void systemPlanetReferencesRoundTrip() throws Exception {
        final XdStarSystem system = new XdStarSystem();
        system.setId("sys-refs");
        final java.util.Set<XdPlanet> planets = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            final XdPlanet planet = new XdPlanet();
            planet.setId(100L + i);
            planet.setName("Planet_" + i);
            planets.add(planet);
        }
        system.setPlanets(planets);

        final Collection<Object> parsed = roundTrip(system);
        final XdStarSystem restored = (XdStarSystem) parsed.iterator().next();

        assertNotNull(restored.getPlanets(), "planets lost");
        assertEquals(4, restored.getPlanets().size(), "planets size mismatch — NULL-дедупликация?");
        for (final XdPlanet p : restored.getPlanets()) {
            assertNotNull(p, "NULL planet in restored collection");
        }
    }

    // =========================================================================
    // 4. Явные null-элементы и пустые коллекции
    // =========================================================================

    @Test
    @DisplayName("Коллекция с явным null-элементом: null обязан сохраниться и восстановиться")
    public void collectionWithExplicitNullItemRoundTrip() throws Exception {
        final XdUniverse universe = new XdUniverse();
        universe.setId("u-nulls");
        final List<XdGalaxy> galaxies = new ArrayList<>();
        galaxies.add(null);
        final XdGalaxy real = new XdGalaxy();
        real.setId("real-galaxy");
        galaxies.add(real);
        galaxies.add(null);
        universe.setGalaxies(galaxies);

        final String yaml = writeToString(universe);
        assertTrue(yaml.contains("type: 'null'"), "writer must mark null items explicitly");

        final Collection<Object> parsed = reader.read(new StringReader(yaml));
        final XdUniverse restored = (XdUniverse) parsed.iterator().next();
        assertNotNull(restored.getGalaxies());
        final List<XdGalaxy> restoredList = new ArrayList<>(restored.getGalaxies());
        assertEquals(3, restoredList.size());
        assertNull(restoredList.get(0), "first null item lost");
        assertNotNull(restoredList.get(1), "real galaxy lost");
        assertNull(restoredList.get(2), "last null item lost");
    }

    @Test
    @DisplayName("Пустая коллекция переживает round-trip")
    public void emptyCollectionRoundTrip() throws Exception {
        final XdUniverse universe = new XdUniverse();
        universe.setId("u-empty");
        universe.setGalaxies(new ArrayList<>());

        final Collection<Object> parsed = roundTrip(universe);
        final XdUniverse restored = (XdUniverse) parsed.iterator().next();
        assertNotNull(restored.getGalaxies(), "empty galaxies collection lost");
        assertTrue(restored.getGalaxies().isEmpty());
    }

    @Test
    @DisplayName("Null-поле верхнего уровня не ломает разбор соседних полей")
    public void nullTopLevelFieldRoundTrip() throws Exception {
        final XdGalaxy galaxy = new XdGalaxy();
        galaxy.setId("g-no-hole");
        galaxy.setHole(null);
        final List<XdStarSystem> systems = new ArrayList<>();
        final XdStarSystem system = new XdStarSystem();
        system.setId("s-1");
        systems.add(system);
        galaxy.setSystems(systems);

        final Collection<Object> parsed = roundTrip(galaxy);
        final XdGalaxy restored = (XdGalaxy) parsed.iterator().next();
        assertEquals("g-no-hole", restored.getId());
        assertNull(restored.getHole(), "hole must stay null");
        assertNotNull(restored.getSystems());
        assertEquals("s-1", restored.getSystems().iterator().next().getId());
    }

    // =========================================================================
    // 5. References-файл и readData-путь
    // =========================================================================

    @Test
    @DisplayName("writeReferences/readReferences: ссылки с id переживают round-trip")
    public void referencesFileRoundTrip() throws Exception {
        final XdUniverse u1 = new XdUniverse();
        u1.setId("ref-u-1");
        final XdUniverse u2 = new XdUniverse();
        u2.setId("ref-u-2");

        final XdStorageObjectIdFieldHolder holder = new XdStorageObjectIdFieldHolder();
        final io.github.xdstorage.utils.XdStorageObjectIdField idField = holder.universeIdField();

        final StringWriter out = new StringWriter();
        writer.writeReferences(out, idField, java.util.Arrays.asList(u1, u2));

        final Collection<Object> parsed = reader.readReferences(new StringReader(out.toString()), idField);
        assertEquals(2, parsed.size(), "references size mismatch");
        for (final Object ref : parsed) {
            assertNotNull(ref, "NULL reference after round-trip");
        }
    }

    @Test
    @DisplayName("readData: путь XdStorageIdentifiableObject разбирает записанные объекты")
    public void readDataPathRoundTrip() throws Exception {
        final XdStar star = new XdStar();
        star.setId("star-data-1");
        star.setName("Star_Data");

        final StringWriter out = new StringWriter();
        writer.writeObjects(out, java.util.Collections.singletonList(star));

        final Collection<?> parsed = reader.readData(new StringReader(out.toString()),
                new XdStorageObjectIdFieldHolder().starIdField());
        assertEquals(1, parsed.size());
        final Object data = parsed.iterator().next();
        assertNotNull(data);
    }

    // =========================================================================
    // Хелперы
    // =========================================================================

    private String writeToString(final Object... objects) throws Exception {
        final StringWriter out = new StringWriter();
        writer.writeObjects(out, java.util.Arrays.asList(objects));
        return out.toString();
    }

    /** Достает XdStorageObjectIdField сущности через метаданные движка. */
    static class XdStorageObjectIdFieldHolder {
        io.github.xdstorage.utils.XdStorageObjectIdField universeIdField() {
            return io.github.xdstorage.utils.XdStorageObjectUtils.getClassInfo(XdUniverse.class).getIdField();
        }

        io.github.xdstorage.utils.XdStorageObjectIdField starIdField() {
            return io.github.xdstorage.utils.XdStorageObjectUtils.getClassInfo(XdStar.class).getIdField();
        }
    }
}
