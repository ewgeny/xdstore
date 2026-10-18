package org.flib.xdstorage.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XdStorageSearchIndexRecordConfermer: учет объектов без идентификаторов")
public class XdStorageSearchIndexRecordConfermerTest {

    @Test
    @DisplayName("Новый confermer: объектов без идентификаторов нет")
    public void testInitiallyEmpty() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();

        assertTrue(confermer.noObjectsWithoutIdentifier());
        assertNull(confermer.getIdForRootObject());
    }

    @Test
    @DisplayName("Регистрация и снятие с учета объекта")
    public void testRegisterUnregister() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();
        final SearchEngineCrewMember member = new SearchEngineCrewMember(1L, "Pilot");

        confermer.registerObjectWithoutIdentifier(member);
        assertFalse(confermer.noObjectsWithoutIdentifier());

        confermer.unregisterObjectWithoutIdentifier(member);
        assertTrue(confermer.noObjectsWithoutIdentifier());
    }

    @Test
    @DisplayName("Объекты разных классов учитываются раздельно")
    public void testPerClassAccounting() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();
        final SearchEngineCrewMember member = new SearchEngineCrewMember(1L, "Pilot");
        final SearchEngineShip ship = new SearchEngineShip();

        confermer.registerObjectWithoutIdentifier(member);
        confermer.registerObjectWithoutIdentifier(ship);
        assertFalse(confermer.noObjectsWithoutIdentifier());

        confermer.unregisterObjectWithoutIdentifier(member);
        assertFalse(confermer.noObjectsWithoutIdentifier(), "корабль все еще без идентификатора");

        confermer.unregisterObjectWithoutIdentifier(ship);
        assertTrue(confermer.noObjectsWithoutIdentifier());
    }

    @Test
    @DisplayName("Несколько объектов одного класса: счетчик снимается только когда пусто")
    public void testMultipleObjectsOfSameClass() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();
        final SearchEngineCrewMember first = new SearchEngineCrewMember(1L, "Pilot");
        final SearchEngineCrewMember second = new SearchEngineCrewMember(2L, "Doctor");

        confermer.registerObjectWithoutIdentifier(first);
        confermer.registerObjectWithoutIdentifier(second);

        confermer.unregisterObjectWithoutIdentifier(first);
        assertFalse(confermer.noObjectsWithoutIdentifier());

        confermer.unregisterObjectWithoutIdentifier(second);
        assertTrue(confermer.noObjectsWithoutIdentifier());
    }

    @Test
    @DisplayName("Повторная регистрация того же объекта: одно снятие с учета очищает класс")
    public void testDuplicateRegistration() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();
        final SearchEngineCrewMember member = new SearchEngineCrewMember(1L, "Pilot");

        confermer.registerObjectWithoutIdentifier(member);
        confermer.registerObjectWithoutIdentifier(member); // Set поглощает дубликат

        confermer.unregisterObjectWithoutIdentifier(member);
        assertTrue(confermer.noObjectsWithoutIdentifier());
    }

    @Test
    @DisplayName("Ид корневого объекта: установка и чтение")
    public void testRootIdStorage() {
        final XdStorageSearchIndexRecordConfermer confermer = new XdStorageSearchIndexRecordConfermer();

        confermer.setIdForRootObject(55L);
        assertEquals(55L, confermer.getIdForRootObject());
    }
}
