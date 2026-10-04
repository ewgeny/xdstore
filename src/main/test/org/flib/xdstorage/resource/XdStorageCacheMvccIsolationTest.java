package org.flib.xdstorage.resource;

import org.flib.xdstorage.transaction.XdStorageTransaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("2. MVCC-изоляция транзакций: изоляция незакоммиченных изменений")
public class XdStorageCacheMvccIsolationTest extends AbstractCacheResourceTest {

    @Test
    @DisplayName("Чтение объекта по ID должно возвращать кэшированную прокси-версию")
    public void testReadById_ShouldReturnClonedAndWrappedVersion() throws Exception {
        TestEntity original = new TestEntity("Data");
        long id = (long) original.hashCode();
        resourceCache.fillCache(Collections.singletonList(original));

        XdStorageTransaction tx = mockTransaction("tx-100");
        TestEntity cloned = new TestEntity("ClonedData");
        when(mockCloner.cloneAndWrap(eq(original), any(), eq(tx))).thenReturn(cloned);

        Object result = resourceCache.read(id, tx);

        assertNotNull(result);
        assertSame(cloned, result);

        Object secondResult = resourceCache.read(id, tx);
        assertSame(result, secondResult);
        verify(mockCloner, times(1)).cloneAndWrap(any(), any(), any());
    }

    @Test
    @DisplayName("Транзакционная изоляция: читатель не должен видеть незакоммиченный Insert")
    public void testRead_ShouldIsolateUncommittedInserts() throws Exception {
        XdStorageTransaction txWriter = mockTransaction("tx-writer");
        XdStorageTransaction txReader = mockTransaction("tx-reader");

        TestEntity newEntity = new TestEntity("Secret");
        long id = 12345L;
        when(mockIdField.get(newEntity)).thenReturn(id);
        when(mockCloner.unwrapAndClone(newEntity)).thenReturn(newEntity);

        resourceCache.insert(newEntity, txWriter);

        Object readResult = resourceCache.read(id, txReader);
        assertNull(readResult, "Другая транзакция не должна видеть незакоммиченную вставку!");

        Object writerResult = resourceCache.read(id, txWriter);
        assertNotNull(writerResult);
    }

    @Test
    @DisplayName("Транзакционная изоляция: читатель должен видеть старую версию, если идет Update")
    public void testRead_ShouldReturnOldVersionDuringConcurrentUpdate() throws Exception {
        TestEntity original = new TestEntity("OldValue");
        long id = 999L;
        when(mockIdField.get(original)).thenReturn(id);
        resourceCache.fillCache(Collections.singletonList(original));

        XdStorageTransaction txWriter = mockTransaction("tx-writer");
        XdStorageTransaction txReader = mockTransaction("tx-reader");

        TestEntity updatedEntity = new TestEntity("NewValue");
        when(mockIdField.get(updatedEntity)).thenReturn(id);
        when(mockCloner.unwrapAndClone(updatedEntity)).thenReturn(updatedEntity);

        resourceCache.update(updatedEntity, txWriter);

        TestEntity readerClone = new TestEntity("OldValue-Clone");
        when(mockCloner.cloneAndWrap(eq(original), any(), eq(txReader))).thenReturn(readerClone);

        Object readerResult = resourceCache.read(id, txReader);
        assertSame(readerClone, readerResult);
    }
}
