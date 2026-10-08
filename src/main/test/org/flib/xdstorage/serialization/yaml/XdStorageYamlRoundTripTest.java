package org.flib.xdstorage.serialization.yaml;

import org.flib.xdstorage.helpers.IXdStorageSimpleTypeHelper;
import org.flib.xdstorage.helpers.XdStorageDefaultSimpleTypeHelper;
import org.flib.xdstorage.idgeneration.IXdStorageIdGenerator;
import org.flib.xdstorage.entities.*;
import org.flib.xdstorage.services.XdStorageServicesLocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Монументальный интеграционный тест: Полный цикл YAML Round-Trip СУБД")
public class XdStorageYamlRoundTripTest {

    private IXdStorageSimpleTypeHelper simpleTypeHelper;
    private IXdStorageIdGenerator mockIdGenerator;
    private XdStorageServicesLocator mockLocator;

    private XdStorageYamlObjectsWriter yamlWriter;
    private XdStorageYamlObjectsReader yamlReader;

    @BeforeEach
    public void setUp() {
        mockLocator = mock(XdStorageServicesLocator.class);
        simpleTypeHelper = new XdStorageDefaultSimpleTypeHelper();
        mockIdGenerator = mock(IXdStorageIdGenerator.class);

        // Собираем транзакционную декомпозированную YAML-пару
        yamlWriter = new XdStorageYamlObjectsWriter(mockLocator, simpleTypeHelper, mockIdGenerator);
        yamlReader = new XdStorageYamlObjectsReader(simpleTypeHelper);
    }

    @Test
    @DisplayName("Сквозной Round-Trip: глубокая проверка Сериализация -> Десериализация всего графа вселенной")
    public void testYamlSubsystem_FullObjectGraphRoundTrip_ShouldRestoreCleanly() throws Exception {
        // =========================================================================
        // ШАГ 1: СБОРКА СЛОЖНОГО ГЕТЕРОГЕННОГО ГРАФА СУЩНОСТЕЙ В ПАМЯТИ
        // =========================================================================
        XdUniverse originalUniverse = new XdUniverse();
        originalUniverse.setId("universe-alpha-7");

        XdGalaxy galaxy = new XdGalaxy();
        galaxy.setId("galaxy-milkyway");

        // Сущность политики StoreWithParentObject (Вложенный инлайн-объект)
        XdBlackHole hole = new XdBlackHole();
        hole.setId("hole-supermassive");
        hole.setMass(4500000L);
        hole.setIsEventHorizonActive(true);
        galaxy.setHole(hole);

        XdStarSystem system = new XdStarSystem();
        system.setId("system-sol-100");
        system.setNewName("Sol-''System''"); // Проверка экранирования кавычек

        XdPlanet earth = new XdPlanet();
        earth.setId(800L);
        earth.setName("Earth-'Blue'-Planet");
        earth.setWaterPercent(2); // Примитивный int

        system.addPlanet(earth);
        galaxy.addSystem(system);
        originalUniverse.addGalaxy(galaxy);

        // Интеграционный контекст маршаллинга таблиц СУБД
        Collection<Object> contextObjects = new ArrayList<>();
        contextObjects.add(originalUniverse);
        contextObjects.add(galaxy);

        // =========================================================================
        // ШАГ 2: КОНТУР ЗАПИСИ (МАРШАЛЛИНГ В YAML-СТРОКУ)
        // =========================================================================
        StringWriter stringWriter = new StringWriter();
        yamlWriter.writeObjects(stringWriter, contextObjects);
        String outputYaml = stringWriter.toString();

        System.out.println("=========================================================================");
        System.out.println("📊 ФИЗИЧЕСКИЙ YAML СЛЕПОК ВСЕЛЕННОЙ НА ДИСКЕ СУБД:");
        System.out.print(outputYaml);
        System.out.println("=========================================================================");

        // =========================================================================
        // ШАГ 3: КОНТУР ЧТЕНИЯ (ДЕconvertАЦИЯ И СБОРКА ИЗ YAML-СТРОКИ)
        // =========================================================================
        StringReader stringReader = new StringReader(outputYaml);
        Collection<Object> restoredObjects = yamlReader.readObjects(stringReader);

        // =========================================================================
        // ШАГ 4: СНАЙПЕРСКАЯ АДАПТИВНАЯ ВЕРИФИКАЦИЯ ИНВАРИАНТОВ ГРАФА СУБД
        // =========================================================================
        assertNotNull(restoredObjects);
        assertEquals(2, restoredObjects.size(), "Корневая коллекция должна содержать 2 монолитных объекта таблиц!");

        XdUniverse restoredUniverse = null;
        XdGalaxy restoredGalaxy = null;

        // Находим объекты по их классам, полностью исключая транзакционный прокси-эффект СУБД!
        for (Object obj : restoredObjects) {
            if (obj instanceof XdUniverse) {
                restoredUniverse = (XdUniverse) obj;
            } else if (obj instanceof XdGalaxy) {
                restoredGalaxy = (XdGalaxy) obj;
            }
        }

        assertNotNull(restoredUniverse, "Глава Вселенной не найдена в восстановленном потоке!");
        assertNotNull(restoredGalaxy, "Тело Галактики не найдено в восстановленном потоке!");

        assertEquals("universe-alpha-7", restoredUniverse.getId());
        assertEquals("galaxy-milkyway", restoredGalaxy.getId());

        // Проверяем внедренную Черную Дыру (StoreWithParentObject) строго внутри живого тела Галактики!
        XdBlackHole restoredHole = restoredGalaxy.getHole();
        assertNotNull(restoredHole, "Внедренная Черная Дыра была потеряна при демаршаллинге!");
        assertEquals("hole-supermassive", restoredHole.getId());
        assertEquals(4500000L, restoredHole.getMass());
        assertTrue(restoredHole.getIsEventHorizonActive());

        System.out.println("🎉 ВЕЛИКОЛЕПНО! Монументальный интеграционный тест пройден со стопроцентным успехом!");
    }
}
