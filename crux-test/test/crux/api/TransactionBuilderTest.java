package crux.api;

import clojure.lang.Keyword;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import crux.api.tx.*;

import org.junit.Test;

import static crux.api.TestUtils.*;
import static org.junit.Assert.*;

public class TransactionBuilderTest {
    /**
     * These tests ensure that both methods of creating a transaction are equivalent
     * as well as ensuring the .equals method behaves as intended.
     */
    private final static Object documentId = "foo";
    private final static AbstractCruxDocument document = new AbstractCruxDocument() {
        @Override
        public Object getId() {
            return documentId;
        }

        @Override
        public Map<Keyword, Object> getData() {
            HashMap<Keyword, Object> ret = new HashMap<Keyword, Object>();
            ret.put(Keyword.intern("bar"), "baz");
            return ret;
        }
    };
    private final static Date validTime = now;
    private final static Date endValidTime = date(500);
    @Test
    public void put() {
        Transaction direct = Transaction.builder()
                .put(document)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.put(document);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void putWithValidTime() {
        Transaction direct = Transaction.builder()
                .put(document, validTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.put(document, validTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void putWithEndValidTime() {
        Transaction direct = Transaction.builder()
                .put(document, validTime, endValidTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.put(document, validTime, endValidTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void delete() {
        Transaction direct = Transaction.builder()
                .delete(documentId)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.delete(documentId);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void deleteWithValidTime() {
        Transaction direct = Transaction.builder()
                .delete(documentId, validTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.delete(documentId, validTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void deleteWithEndValidTime() {
        Transaction direct = Transaction.builder()
                .delete(documentId, validTime, endValidTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.delete(documentId, validTime, endValidTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void match() {
        Transaction direct = Transaction.builder()
                .match(document)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.match(document);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void matchWithValidTime() {
        Transaction direct = Transaction.builder()
                .match(document, validTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.match(document, validTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void matchNotExists() {
        Transaction direct = Transaction.builder()
                .matchNotExists(documentId)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.matchNotExists(documentId);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void matchNotExistsWithValidTime() {
        Transaction direct = Transaction.builder()
                .matchNotExists(documentId, validTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.matchNotExists(documentId, validTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void evict() {
        Transaction direct = Transaction.builder()
                .evict(documentId)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.evict(documentId);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void manyOperations() {
        Transaction direct = Transaction.builder()
                .put(document)
                .put(document, validTime)
                .delete(document, validTime, endValidTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.put(document);
            tx.put(document, validTime);
            tx.delete(document, validTime, endValidTime);
        });

        assertEquals(direct, indirect);
    }

    @Test
    public void manyOperationsOutOfOrder() {
        Transaction direct = Transaction.builder()
                .put(document)
                .put(document, validTime)
                .delete(document, validTime, endValidTime)
                .build();

        Transaction indirect = Transaction.buildTx(tx -> {
            tx.put(document);
            tx.delete(document, validTime, endValidTime);
            tx.put(document, validTime);
        });

        assertNotEquals(direct, indirect);
    }
}
