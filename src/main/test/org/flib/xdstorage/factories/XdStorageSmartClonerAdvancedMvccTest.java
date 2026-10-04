package org.flib.xdstorage.factories;

import org.flib.xdstorage.btree.XdStorageBTree;
import org.flib.xdstorage.btree.XdStorageBTreeNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("3. Верификация сложных MVCC-инвариантов и защиты от StackOverflow рекурсий")
public class XdStorageSmartClonerAdvancedMvccTest extends AbstractClonerTest {

    @Test
    @DisplayName("Защита от StackOverflow: ORM обязан корректно обрабатывать перекрестные ссылки графа")
    public void testCloneAndWrap_WithCircularDependency_ShouldNotThrowStackOverflow() throws Exception {
        // Строим перекрестный граф зацикленных данных
        CyclicParent parent = new CyclicParent();
        parent.setId(1L);

        CyclicChild child = new CyclicChild();
        child.setId(2L);

        parent.setChild(child);
        child.setParent(parent); // Зациклили!

        // Проверяем инвариант безопасности ORM: глубокое копирование графа объектов
        // обязано бесшовно разрулить циклическую связь без ухода в StackOverflowError!
        assertDoesNotThrow(() -> {
            Object result = smartCloner.cloneAndWrap(parent, mockStorage, mockTx);
            assertNotNull(result);
        }, "Критический дефект! Клонер уходит в бесконечную рекурсию при обработке циклических ссылок!");
    }

    @Test
    @DisplayName("Критический инвариант СУБД: Класс XdStorageBTree ЗАПРЕЩЕНO подвергать мутациям клонирования")
    public void testUnwrapAndClone_ShouldBypassBTreeInfrastructure_ToPreventMvccLeaks() throws Exception {
        XdStorageBTree mockTree = mock(XdStorageBTree.class);

        // =========================================================================
        // ВЕРИФИКАЦИЯ АЛГОРИТМИЧЕСКОГО БАРЬЕРА СУБД (Защита от перетирки версий индексов):
        // Метод unwrapAndClone обязан вернуть ТОТ ЖЕ САМЫЙ инстанс (Identity) для служебных
        // инфраструктурных классов дерева СУБД, пресекая рекурсивный обход JavaBeans полей,
        // что полностью спасает базу данных от ошибок concurrent modification!
        // =========================================================================
        Object result = smartCloner.unwrapAndClone(mockTree);

        assertSame(mockTree, result, "Клонер нарушил барьер и попытался переклонировать инстанс XdStorageBTree!");
    }

    @Test
    @DisplayName("Критический инвариант СУБД: Класс XdStorageBTreeNode обязан возвращать исходную ссылку (Identity)")
    public void testUnwrapAndClone_ShouldBypassBTreeNodeInfrastructure() throws Exception {
        XdStorageBTreeNode mockNode = mock(XdStorageBTreeNode.class);

        Object result = smartCloner.unwrapAndClone(mockNode);

        assertSame(mockNode, result, "Клонер обязан пропустить рекурсивную деструкцию полей для узлов B+ Дерева!");
    }
}
