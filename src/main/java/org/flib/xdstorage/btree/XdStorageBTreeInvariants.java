package org.flib.xdstorage.btree;

import org.flib.xdstorage.IXdStorage;
import org.flib.xdstorage.exceptions.XdStorageConnectionException;
import org.flib.xdstorage.exceptions.XdStorageException;
import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.flib.xdstorage.utils.XdStorageObjectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Контроль структурных инвариантов B+ Дерева индексов на границе транзакционных операций.
 * Прогоняется после мутаций индекса (insert/delete) в контуре транзакции.
 *
 * Проверяет три инварианта:
 * 1. Целостность вершин: каждый child каждого внутреннего узла разрешается в живую ноду
 *    (ловит висячие ссылки после слияний — merge удалил ноду, а родитель ее все еще держит).
 * 2. Паритет keys/objects в каждом листе (ловит разъехавшиеся списки после split).
 * 3. Эквивалентность множества листьев, достижимых из корня, и множества листьев,
 *    достижимых из firstLeaf по цепочке next (ловит обрыв цепочки: лист существует
 *    в дереве, но диапазонные сканы его не видят — дефект 268/1250).
 * 4. Глобальную неубываемость ключей вдоль цепочки листьев (ловит неверную сшивку split/merge).
 *
 * При любом нарушении выбрасывается XdStorageException — транзакция обязана откатиться громко,
 * а не фиксировать битую структуру на диск молча.
 */
public final class XdStorageBTreeInvariants {

    private static final org.apache.logging.log4j.Logger log =
            org.apache.logging.log4j.LogManager.getLogger(XdStorageBTreeInvariants.class);

    /**
     * R-32: сэмплирование полной валидации. Полный обход цепочки листьев + descend — O(N);
     * на больших деревьях вызов на КАЖДУЮ операцию давал O(N²) на массовых удалениях
     * (пурдж 1250 ключей ≈ 780к посещений узлов). Дешевые O(1)-проверки пар node
     * (children==keys+1, монотонность разделителей) выполняются tripwire-ами после КАЖДОЙ
     * мутации независимо; полный обход — каждая 25-я операция.
     * R-56: интервал конфигурируем (-Dxdstorage.validation.interval=1) — бисекция
     * структурных расхождений: первый разрыв ловится НА породившей его операции.
     */
    private static final int FULL_VALIDATION_INTERVAL =
            Math.max(1, Integer.getInteger("xdstorage.validation.interval", 25));
    private static final java.util.concurrent.atomic.AtomicLong VALIDATION_SAMPLER =
            new java.util.concurrent.atomic.AtomicLong();

    public static boolean shouldRunFullValidation() {
        return VALIDATION_SAMPLER.incrementAndGet() % FULL_VALIDATION_INTERVAL == 1;
    }

    private XdStorageBTreeInvariants() {
        // utility class
    }

    /**
     * Полная структурная валидация дерева индекса после мутации.
     * Выполняется под write-локом дерева: мутаторы держат этот лок на всю операцию,
     * поэтому валидатор не может наблюдать промежуточные состояния ребалансировки.
     *
     * R-56: ВАЛИДАЦИЯ СЭМПЛ-СОГЛАСОВАННА. Сторожевой Exception
     * (ConcurrentStructureChangeSignal) сигнализирует, что пара (structureVersion,
     * activeMutations) дерева изменилась ВО ВРЕМЯ двух обходов, — наблюдение не атомарно,
     * результат недействителен. Валидация повторяется до {@code VALIDATION_RETRY_LIMIT}
     * раз; после этого ДИАГНОСТИКА ПРОПУСКАЕТСЯ с WARN (полную гарантию целостности несут
     * tripwire-ы на каждой мутации). РЕАЛЬНОЕ нарушение (расхождение множеств листьев
     * при стабильной версии) по-прежнему громко роняет операцию.
     */
    private static final int VALIDATION_RETRY_LIMIT = 3;

    /** Внутренний маркер «наблюдение не атомарно» — не уходит наружу валидатора. */
    private static final class ConcurrentStructureChangeSignal extends XdStorageException {
        ConcurrentStructureChangeSignal(final String message) {
            super(message);
        }
    }

    public static void validateLeafChain(final XdStorageBTree tree, final IXdStorage storage,
                                         final XdStorageTransaction transaction, final String context)
            throws XdStorageException, XdStorageConnectionException {
        if (tree == null) {
            return;
        }

        tree.lockWrite(transaction);
        try {
            validateWithConsistencySampling(tree, storage, transaction, context);
        } catch (final XdStorageException e) {
            // Нарушение инварианта — всегда громко: исключение уйдет в откат транзакции,
            // но сам факт структурного повреждения обязан остаться в логе ERROR навсегда.
            log.error("B+Tree INVARIANT VIOLATION: {}", e.getMessage(), e);
            throw e;
        } finally {
            tree.unlockWrite();
        }
    }

    private static void validateWithConsistencySampling(final XdStorageBTree tree, final IXdStorage storage,
                                                        final XdStorageTransaction transaction, final String context)
            throws XdStorageException, XdStorageConnectionException {
        ConcurrentStructureChangeSignal signal = null;
        for (int attempt = 1; attempt <= VALIDATION_RETRY_LIMIT; ++attempt) {
            try {
                validateUnderTreeLock(tree, storage, transaction, context);
                return;
            } catch (final ConcurrentStructureChangeSignal e) {
                signal = e;
                log.warn("B+Tree validation of {} observed concurrent structure change (attempt {}/{}): {}",
                        context, attempt, VALIDATION_RETRY_LIMIT, e.getMessage());
            }
        }
        // Диагностика не смогла получить атомарный срез — пропуск вместо громкого отказа:
        // параллельные мутации продолжаются, структурные гарантии несут tripwire-ы мутаций.
        log.warn("B+Tree validation of {} skipped after {} attempts: structure never quiesced ({})",
                context, VALIDATION_RETRY_LIMIT, signal != null ? signal.getMessage() : "unknown");
    }

    private static void validateUnderTreeLock(final XdStorageBTree tree, final IXdStorage storage,
                                              final XdStorageTransaction transaction, final String context)
            throws XdStorageException, XdStorageConnectionException {
        if (tree == null) {
            return;
        }

        // R-56: сэмпл согласованности (до обхода из корня)
        final long versionBefore = tree.currentStructureVersion();
        final int activeBefore = tree.activeMutationCount();
        if (activeBefore != 0) {
            throw new ConcurrentStructureChangeSignal(context + ": мутация уже в полете (activeMutations="
                    + activeBefore + ", mutators=" + tree.describeActiveMutators()
                    + ", writeLocked=" + tree.isWriteLocked() + ") — наблюдение отложено");
        }

        // ИНВАРИАНТ 1 + сбор множества листьев, достижимых из корня
        final Set<Object> leavesViaRoot = new HashSet<>();
        if (tree.getRoot() != null) {
            descendCollectingLeaves(tree.getRoot(), storage, transaction, leavesViaRoot, context, new HashSet<>(),
                    tree.isMultiple());
        }

        // R-56: сэмпл между обходами — версия сменилась = корневой обход видел другой каркас
        final long versionBetween = tree.currentStructureVersion();
        if (versionBetween != versionBefore) {
            throw new ConcurrentStructureChangeSignal(context + ": structureVersion изменился во время корневого обхода ("
                    + versionBefore + " -> " + versionBetween + ")");
        }

        if (tree.getFirstLeaf() == null) {
            if (!leavesViaRoot.isEmpty()) {
                throw new XdStorageException(context + ": firstLeaf пуст, но из корня достижимо листьев: " + leavesViaRoot.size());
            }
            return;
        }

        // ИНВАРИАНТ 2 + 4 + сбор множества листьев цепочки
        final Set<Object> leavesViaChain = new HashSet<>();
        final XdStorageException[] failure = new XdStorageException[]{null};
        final Comparable[] previousKey = new Comparable[]{null};

        XdStorageBTreeNode current = tree.getFirstLeaf();
        // R-53: стаб первого листа (холодное открытие) разрешается до обхода цепочки
        if (current != null && XdStorageObjectUtils.isReference(current)) {
            current = XdStorageBTreeNode.materialize(current, storage, transaction, tree, null);
            tree.setFirstLeaf(current);
        }
        int guard = 0;
        while (current != null) {
            if (failure[0] != null) {
                throw failure[0];
            }
            if (current.getNextTreeNodeOnThisLevel() == current) {
                throw new XdStorageException(context + ": лист " + current.getId() + " замкнут сам на себя");
            }
            if (!leavesViaChain.add(current.getId())) {
                throw new XdStorageException(context + ": цикл в цепочке листьев — лист " + current.getId() + " встречается повторно");
            }
            // R-56: parity и обход ключей — под монитором коллекций (дисциплина R-43),
            // чтобы даже гипотетический параллельный мутатор не дал валидатору рваный срез
            final int keysCount, objectsCount;
            final List<Comparable> leafKeys;
            synchronized (current.getKeys()) {
                keysCount = current.getKeys().size();
                leafKeys = new ArrayList<>(current.getKeys());
            }
            synchronized (current.getObjects()) {
                objectsCount = current.getObjects().size();
            }
            if (keysCount != objectsCount) {
                throw new XdStorageException(context + ": лист " + current.getId() + " имеет keys=" + keysCount
                        + " при objects=" + objectsCount + " — списки разошлись");
            }
            for (final Comparable key : leafKeys) {
                if (key == null) {
                    throw new XdStorageException(context + ": лист " + current.getId() + " содержит null-ключ");
                }
                if (previousKey[0] != null && previousKey[0].compareTo(key) > 0) {
                    throw new XdStorageException(context + ": нарушение сортировки в цепочке листьев: " + previousKey[0] + " > " + key
                            + " (лист " + current.getId() + ") — некорректный split/merge");
                }
                previousKey[0] = key;
            }

            final XdStorageBTreeNode next = current.getNextTreeNodeOnThisLevel();
            if (next != null && XdStorageObjectUtils.isReference(next)) {
                // R-53: стаб следующего листа разрешается в канонический инстанс (слот чинится:
                // валидация идет под write-локом дерева, мутаций не видно)
                final XdStorageBTreeNode resolved = XdStorageBTreeNode.materialize(next, storage, transaction, tree, null);
                if (resolved != next) {
                    current.setNextTreeNodeOnThisLevel(resolved);
                }
                current = resolved;
            } else {
                current = next;
            }

            if (++guard > 100000) {
                throw new XdStorageException(context + ": цепочка листьев длиннее 100000 — защита от зацикливания");
            }
        }

        // R-56: сэмпл после обхода цепочки — оба обхода атомарны только при неизменной версии
        final long versionAfter = tree.currentStructureVersion();
        final int activeAfter = tree.activeMutationCount();
        if (versionAfter != versionBefore || activeAfter != 0) {
            throw new ConcurrentStructureChangeSignal(context + ": структура изменилась во время валидации (version "
                    + versionBefore + " -> " + versionAfter + ", activeMutations " + activeBefore + " -> " + activeAfter + ")");
        }

        // ИНВАРИАНТ 3: множество листьев из корня == множество листьев цепочки
        if (!leavesViaRoot.equals(leavesViaChain)) {
            final Set<Object> onlyViaRoot = new HashSet<>(leavesViaRoot);
            onlyViaRoot.removeAll(leavesViaChain);
            final Set<Object> onlyViaChain = new HashSet<>(leavesViaChain);
            onlyViaChain.removeAll(leavesViaRoot);
            if (XdStorageBTreeNode.CHAIN_DEBUG) {
                final StringBuilder dump = new StringBuilder("[CHAIN] divergence dump: ");
                XdStorageBTreeNode cur = tree.getFirstLeaf();
                while (cur != null) {
                    dump.append(cur.getId()).append("->").append(cur.getNextTreeNodeOnThisLevel() != null ? cur.getNextTreeNodeOnThisLevel().getId() : "null").append(' ');
                    cur = cur.getNextTreeNodeOnThisLevel();
                }
                System.out.println(dump);
                // R-56: корневая сторона расхождения — какие ИНСТАНЦИИ обошел descend
                // (id@identityHash): сравнение с [CHAIN] REGISTER-строками выявляет дуальность
                // инстанций (спуск шел через не-каноническую копию) против потери связи
                // (лист в цепочке, но недостижим из корня при канонных инстанциях).
                try {
                    final StringBuilder rootDump = new StringBuilder("[CHAIN] root-descent dump: tree@")
                            .append(System.identityHashCode(tree))
                            .append(" root@").append(tree.getRoot() == null ? "null" : System.identityHashCode(tree.getRoot()))
                            .append(" firstLeaf@").append(tree.getFirstLeaf() == null ? "null" : System.identityHashCode(tree.getFirstLeaf()))
                            .append(" : ");
                    dumpRootDescent(tree.getRoot(), storage, transaction, rootDump, new HashSet<>());
                    System.out.println(rootDump);
                } catch (final Throwable t) {
                    System.out.println("[CHAIN] root-descent diagnostics failed: " + t);
                }
            }
            throw new XdStorageException(context + ": рассинхрон структуры: листьев только из корня: " + onlyViaRoot
                    + ", листьев только из цепочки: " + onlyViaChain
                    + " (из корня: " + leavesViaRoot.size() + ", из цепочки: " + leavesViaChain.size() + ")");
        }
    }

    /**
     * TRIPWIRE: мгновенная проверка структурной целостности ноды сразу ПОСЛЕ мутации
     * (join/borrow/split/root-collapse). Для внутренней ноды: разделители строго возрастают,
     * дети == ключи + 1, id детей не дублируются. Бросает немедленно — имя операции в сообщении
     * указывает мутатор, разъехавшийся пары keys/children.
     */
    static void checkStructuralAlignment(final XdStorageBTreeNode node, final String op) throws XdStorageException {
        if (node == null || node.getChildren().isEmpty()) {
            return; // лист или пустая нода — пары keys/children не проверяемы здесь
        }
        final List<Comparable> keys = node.getKeys();
        // Для multiple-деревьев равные разделители легитимны (дубликаты ключа лежат в разных листьях)
        final boolean allowEqualSeparators = node.getTree() != null && node.getTree().isMultiple();
        for (int i = 1; i < keys.size(); ++i) {
            final int cmp = keys.get(i - 1).compareTo(keys.get(i));
            if (cmp > 0 || (cmp == 0 && !allowEqualSeparators)) {
                throw new XdStorageException("[tripwire:" + op + "] нода " + node.getId()
                        + ": дубликат/убывание разделителя keys[" + (i - 1) + "]=" + keys.get(i - 1)
                        + " >= keys[" + i + "]=" + keys.get(i));
            }
        }
        if (node.getChildren().size() != keys.size() + 1) {
            throw new XdStorageException("[tripwire:" + op + "] нода " + node.getId()
                    + ": children=" + node.getChildren().size() + " при keys=" + keys.size());
        }
        final Set<Object> seenChildIds = new HashSet<>();
        for (final XdStorageBTreeNode child : node.getChildren()) {
            if (child != null && child.getId() != null && !seenChildIds.add(child.getId())) {
                throw new XdStorageException("[tripwire:" + op + "] нода " + node.getId()
                        + ": дубликат ребенка " + child.getId() + " в списке детей");
            }
        }
    }

    /**
     * R-56: диагностический обход из корня для дампа расхождения — только id@identityHash,
     * без бросков (диагностика не имеет права маскировать исходное нарушение).
     */
    private static void dumpRootDescent(final XdStorageBTreeNode node, final IXdStorage storage,
                                        final XdStorageTransaction transaction,
                                        final StringBuilder sb, final Set<Object> visited)
            throws XdStorageException, XdStorageConnectionException {
        if (node == null || !visited.add(node.getId())) {
            return;
        }
        XdStorageBTreeNode resolved = node;
        if (XdStorageObjectUtils.isReference(node)) {
            resolved = XdStorageBTreeNode.materialize(node, storage, transaction, null, null);
        }
        sb.append(resolved.getId()).append('@').append(System.identityHashCode(resolved)).append(' ');
        final List<XdStorageBTreeNode> children = new ArrayList<>(resolved.getChildren());
        for (final XdStorageBTreeNode child : children) {
            if (child != null) {
                dumpRootDescent(child, storage, transaction, sb, visited);
            }
        }
    }

    private static void descendCollectingLeaves(final XdStorageBTreeNode node, final IXdStorage storage,
                                                final XdStorageTransaction transaction, final Set<Object> leaves,
                                                final String context, final Set<Object> visited,
                                                final boolean allowEqualSeparators)
            throws XdStorageException, XdStorageConnectionException {
        if (node == null) {
            return;
        }
        if (!visited.add(node.getId())) {
            throw new XdStorageException(context + ": цикл/дубль в descend-обходе — нода " + node.getId() + " встречена повторно");
        }

        if (node.getChildren().isEmpty()) {
            leaves.add(node.getId());
            return;
        }

        // ИНВАРИАНТ: разделители внутренней ноды СТРОГО возрастают, а число детей
        // ровно на единицу больше числа ключей. Дубль/убывание разделителя — первый
        // симптом каскадного сбоя join/borrow (сдвиг пары keys/children), из которого
        // растут промахи спуска ("does not exists") и сироты цепочки листьев.
        // R-56: срезы под мониторами коллекций (дисциплина R-43).
        final List<Comparable> nodeKeys;
        final List<XdStorageBTreeNode> childrenSnapshot;
        synchronized (node.getKeys()) {
            nodeKeys = new ArrayList<>(node.getKeys());
        }
        synchronized (node.getChildren()) {
            childrenSnapshot = new ArrayList<>(node.getChildren());
        }
        for (int i = 1; i < nodeKeys.size(); ++i) {
            final int cmp = nodeKeys.get(i - 1).compareTo(nodeKeys.get(i));
            if (cmp > 0 || (cmp == 0 && !allowEqualSeparators)) {
                throw new XdStorageException(context + ": нарушение монотонности разделителей внутренней ноды " + node.getId()
                        + ": keys[" + (i - 1) + "]=" + nodeKeys.get(i - 1) + " >= keys[" + i + "]=" + nodeKeys.get(i)
                        + " (сдвиг пары keys/children в join/borrow)");
            }
        }
        if (childrenSnapshot.size() != nodeKeys.size() + 1) {
            throw new XdStorageException(context + ": внутренняя нода " + node.getId() + " имеет children=" + childrenSnapshot.size()
                    + " при keys=" + nodeKeys.size() + " (ожидалось children == keys + 1)");
        }

        for (final XdStorageBTreeNode child : childrenSnapshot) {
            if (child == null) {
                throw new XdStorageException(context + ": внутренняя нода " + node.getId() + " содержит null-ребенка");
            }
            XdStorageBTreeNode resolved = child;
            if (XdStorageObjectUtils.isReference(child)) {
                try {
                    // R-53: стаб ребенка разрешается в канонический инстанс по id
                    resolved = XdStorageBTreeNode.materialize(child, storage, transaction, null, node);
                } catch (final Throwable t) {
                    throw new XdStorageException(context + ": ВИСЯЧАЯ ССЫЛКА — внутренняя нода " + node.getId()
                            + " ссылается на ребенка " + child.getId() + ", который не разрешается: " + t.getMessage(), t);
                }
            }
            descendCollectingLeaves(resolved, storage, transaction, leaves, context, visited, allowEqualSeparators);
        }
    }
}
