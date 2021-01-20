package crux.api;

import clojure.lang.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;

import crux.api.tx.*;

import org.junit.*;

import static crux.api.TestUtils.*;
import static org.junit.Assert.*;

public class JCruxNodeTest {
    private static final Keyword versionId = Keyword.intern("version");
    private static Map<Keyword, Object> config;
    private static final TestDocument document = new TestDocument(0);
    private static final Transaction putTransaction = Transaction.buildTx(tx -> {
       tx.put(document);
    });

    private ICruxAPI node;

    @BeforeClass
    public static void beforeClass() {
        HashMap<Keyword, Object> nodeConfig = new HashMap<>();
        nodeConfig.put(Keyword.intern("slow-queries-min-threshold"), Duration.ofSeconds(-1));
        HashMap<Keyword, Object> _config = new HashMap<>();
        _config.put(Keyword.intern("crux/node"), nodeConfig);
        config = _config;
    }

    @Before
    public void before() {
        node = Crux.startNode(config);
    }

    @After
    public void after() {
        close(node);
        node = null;
    }

    /*
     ICruxIngestAPI Tests
     */
    @Test
    public void submitTxTest() {
        TransactionInstant tx = p();

        assertEquals(0L, (long) tx.getId());
        assertNotNull(tx.getTime());
    }

    @Test
    public void openTxLogTest() {
        TransactionInstant tx = p();
        sync();

        ICursor<Map<Keyword, ?>> txLog = node.openTxLog(-1L, false);
        assertTrue(txLog.hasNext());
        Map<Keyword, ?> txLogEntry = txLog.next();
        assertFalse(txLog.hasNext());

        assertNull(getTransaction(txLogEntry));
        assertEquals(tx, getTransactionInstant(txLogEntry));

        txLog = node.openTxLog(-1L, true);
        assertTrue(txLog.hasNext());
        txLogEntry = txLog.next();
        assertFalse(txLog.hasNext());

        assertEquals(tx, getTransactionInstant(txLogEntry));
        assertNotNull(getTransaction(txLogEntry));
    }

    /*
    ICruxAPI tests.
    Note that not testing the ones that return an ICruxDatasource as these will be tested as part of JCruxDatasourceTest
     */
    @Test
    public void statusTest() {
        Map<Keyword, ?> status = node.status();
        assertNotNull(status);
        assertContains(status, false,"crux.version/version");
        assertContains(status, true, "crux.version/revision");
        assertContains(status, false,"crux.kv/kv-store");
        assertContains(status, false,"crux.kv/estimate-num-keys");
        assertContains(status, true, "crux.kv/size");
        assertContains(status, false, "crux.index/index-version");
        assertContains(status, true, "crux.doc-log/consumer-state");
        assertContains(status, true, "crux.tx-log/consumer-state");
    }

    @Test(expected = NodeOutOfSyncException.class)
    public void hasTxCommittedThrowsTest() {
        TransactionInstant tx = p();
        node.hasTxCommitted(tx);
    }

    @Test
    public void hasTxCommittedTest() {
        TransactionInstant tx = p();
        sync();
        assertTrue(node.hasTxCommitted(tx));
    }

    @Test(expected = TimeoutException.class)
    public void syncThrowsTest() {
        for (int i=0; i<100; i++) {
            p();
        }
        node.sync(Duration.ZERO);
    }

    @Test
    public void syncTest() {
        TransactionInstant tx = p();
        Date txTime = tx.getTime();
        Date fromSync = sync();
        assertEquals(txTime, fromSync);
    }

    @Test(expected = TimeoutException.class)
    public void awaitTxTimeThrowsTest() {
        for (int i=0; i<100; i++) {
            p();
        }
        TransactionInstant tx = p();

        Date txTime = tx.getTime();
        node.awaitTxTime(txTime, Duration.ZERO);
    }

    @Test
    public void awaitTxTimeTest() {
        TransactionInstant tx = p();

        Date txTime = tx.getTime();
        Date past = Date.from(txTime.toInstant().minusMillis(100));
        Date fromAwait = node.awaitTxTime(past, duration);
        assertEquals(txTime, fromAwait);
    }

    @Test(expected = TimeoutException.class)
    public void awaitTxThrowsTest() {
        for (int i=0; i<100; i++) {
            p();
        }
        TransactionInstant tx = p();
        node.awaitTx(tx, Duration.ZERO);
    }

    @Test
    public void awaitTxTest() {
        TransactionInstant tx = p();
        node.awaitTx(tx, duration);
    }

    @Test
    public void listenTest() {
        final Object[] events = new Object[]{null};
        AutoCloseable listener = node.listen(ICruxAPI.TX_INDEXED_EVENT_OPTS, (Map<Keyword,?> e) -> {
            events[0] = e;
        });
        TransactionInstant tx = p();
        sync();
        sleep(100);
        @SuppressWarnings("unchecked")
        Map<Keyword, ?> event = (Map<Keyword, ?>) events[0];
        assertNotNull(event);
        assertEquals(5, event.size());
        assertEquals(Keyword.intern("crux/indexed-tx"), event.get(Keyword.intern("crux/event-type")));
        assertTrue((Boolean) event.get(Keyword.intern("committed?")));
        assertEquals(tx.getTime(), event.get(TX_TIME));
        assertEquals(0L, event.get(TX_ID));

        //TODO: Reassert
        //assertTxOps((LazySeq) event.get(Keyword.intern("crux/tx-ops")));

        try {
            listener.close();
        } catch (Exception e) {
            fail();
        }

        events[0] = null;

        p();
        sync();
        sleep(100);

        assertNull(events[0]);
    }

    @Test
    public void latestCompletedTxTest() {
        TransactionInstant tx = p();
        sync();
        TransactionInstant latest = node.latestCompletedTx();
        assertEquals(tx, latest);
    }

    @Test
    public void latestSubmittedTxTest() {
        assertNull(node.latestSubmittedTx());
        TransactionInstant tx = p();
        TransactionInstant latest = node.latestSubmittedTx();
        //Latest Submitted doesn't give us the TxTime
        TransactionInstant compare = TransactionInstant.factory(tx.getId());
        assertEquals(compare, latest);
    }

    @Test
    public void attributeStatsTest() {
        p();
        sync();
        Map<Keyword, ?> stats = node.attributeStats();
        assertEquals(1, stats.get(DB_ID));
        assertEquals(1, stats.get(versionId));
        assertEquals(2, stats.size());
    }

    @Test
    public void activeQueriesTest() {
        List<IQueryState> active = node.activeQueries();
        assertEquals(0, active.size());
    }

    @Test
    public void recentQueriesTest() {
        p();
        sync();
        query();
        sleep(10);
        List<IQueryState> recent = node.recentQueries();
        assertEquals(1, recent.size());
    }

    @Test
    public void slowestQueriesTest() {
        p();
        sync();
        query();
        sleep(10);
        List<IQueryState> slowest = node.slowestQueries();
        assertEquals(1, slowest.size());
    }

    /*
    Utils
     */
    private void assertContains(Map<Keyword, ?> map, boolean canBeNull, String string) {
        Keyword keyword = Keyword.intern(string);
        if (canBeNull) {
            assertTrue(map.containsKey(keyword));
        }
        else {
            assertNotNull(map.get(keyword));
        }
    }

    private TransactionInstant p() {
        return tx(node, putTransaction);
    }

    private Collection<List<?>> query() {
        HashMap<Keyword, Object> map = new HashMap<>();
        map.put(Keyword.intern("find"), PersistentVector.create(listOf(Symbol.intern("d"))));
        map.put(Keyword.intern("where"), PersistentVector.create(listOf(PersistentVector.create(listOf(Symbol.intern("d"), DB_ID)))));
        return node.db().query(PersistentArrayMap.create(map));
    }

    private Date sync() {
        return node.sync(duration);
    }
}
