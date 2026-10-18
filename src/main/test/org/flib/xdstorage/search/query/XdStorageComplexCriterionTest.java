package org.flib.xdstorage.search.query;

import org.flib.xdstorage.search.data.XdStorageSearchIndexRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("XdStorageComplexCriterion: последовательная логика AND/OR с короткими замыканиями")
public class XdStorageComplexCriterionTest {

    /** Критерий-счетчик: фиксирует каждое вычисление passed(). */
    private static final class CountingCriterion implements IXdStorageCriterion {
        private final boolean result;
        private int evaluations = 0;

        private CountingCriterion(final boolean result) {
            this.result = result;
        }

        @Override
        public boolean passed(final XdStorageSearchIndexRecord record) {
            evaluations++;
            return result;
        }
    }

    private static CountingCriterion T() {
        return new CountingCriterion(true);
    }

    private static CountingCriterion F() {
        return new CountingCriterion(false);
    }

    private static final XdStorageSearchIndexRecord RECORD = new XdStorageSearchIndexRecord();

    @Test
    @DisplayName("Пустой критерий проходит всегда")
    public void testEmptyPasses() {
        assertTrue(new XdStorageComplexCriterion().passed(RECORD));
    }

    @Test
    @DisplayName("Одиночный критерий возвращает собственный результат")
    public void testSingleCriterion() {
        final XdStorageComplexCriterion t = new XdStorageComplexCriterion(T());
        final XdStorageComplexCriterion f = new XdStorageComplexCriterion(F());

        assertTrue(t.passed(RECORD));
        assertFalse(f.passed(RECORD));
    }

    @Test
    @DisplayName("AND: истинная цепочка продолжается, ложная прерывает вычисление")
    public void testAndShortCircuitOnFalse() {
        final XdStorageComplexCriterion tAndT = new XdStorageComplexCriterion(T());
        tAndT.and(T());
        assertTrue(tAndT.passed(RECORD));

        final CountingCriterion c1 = T();
        final CountingCriterion c2 = F();
        final CountingCriterion c3 = T();
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(c1);
        chain.and(c2).and(c3);

        assertFalse(chain.passed(RECORD));
        // короткое замыкание AND: после false третий критерий не вычисляется
        assertEquals(1, c1.evaluations);
        assertEquals(1, c2.evaluations);
        assertEquals(0, c3.evaluations);
    }

    @Test
    @DisplayName("OR: ложная цепочка продолжается, истинная замыкает ветку")
    public void testOrShortCircuitOnTrue() {
        final CountingCriterion c1 = F();
        final CountingCriterion c2 = T();
        final CountingCriterion c3 = F();
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(c1);
        chain.or(c2).or(c3);

        assertTrue(chain.passed(RECORD));
        // короткое замыкание OR: после true третий критерий не вычисляется
        assertEquals(1, c1.evaluations);
        assertEquals(1, c2.evaluations);
        assertEquals(0, c3.evaluations);
    }

    @Test
    @DisplayName("F or F or T -> true (все ветви вычислены)")
    public void testOrChainAllFalseUntilTrue() {
        final CountingCriterion c1 = F();
        final CountingCriterion c2 = F();
        final CountingCriterion c3 = T();
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(c1);
        chain.or(c2).or(c3);

        assertTrue(chain.passed(RECORD));
        assertEquals(1, c1.evaluations);
        assertEquals(1, c2.evaluations);
        assertEquals(1, c3.evaluations);
    }

    @Test
    @DisplayName("Логика лево-ассоциативна без приоритета операций: T or F and F -> false")
    public void testSequentialSemanticsOrThenAnd() {
        // Фактическая семантика: OR при result=true НЕ поглощает критерий (continue без it.next()),
        // поэтому следующий критерий (c2) достается СЛЕДУЮЩЕЙ функции (AND): (T [or-skip]) and F = false.
        // Третий критерий не вычисляется - функции закончились.
        final CountingCriterion c1 = T();
        final CountingCriterion c2 = F();
        final CountingCriterion c3 = F();
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(c1);
        chain.or(c2).and(c3);

        assertFalse(chain.passed(RECORD));
        assertEquals(1, c2.evaluations, "второй критерий поглощен функцией AND");
        assertEquals(0, c3.evaluations, "третий критерий не достигнут: функции закончились");
    }

    @Test
    @DisplayName("F or T and F -> false (все критерии вычислены)")
    public void testSequentialSemanticsOrTrueThenAndFalse() {
        final CountingCriterion c1 = F();
        final CountingCriterion c2 = T();
        final CountingCriterion c3 = F();
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(c1);
        chain.or(c2).and(c3);

        assertFalse(chain.passed(RECORD));
        assertEquals(1, c1.evaluations);
        assertEquals(1, c2.evaluations);
        assertEquals(1, c3.evaluations);
    }

    @Test
    @DisplayName("T and F or T -> true")
    public void testAndFalseThenOrTrue() {
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion(T());
        chain.and(F()).or(T());

        assertTrue(chain.passed(RECORD));
    }

    @Test
    @DisplayName("and()/or() возвращают this для текучего конструирования")
    public void testFluentApi() {
        final XdStorageComplexCriterion chain = new XdStorageComplexCriterion();

        assertSame(chain, chain.and(T()));
        assertSame(chain, chain.or(F()));
        assertTrue(chain.passed(RECORD));
    }
}
