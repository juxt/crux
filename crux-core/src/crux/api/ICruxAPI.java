package crux.api;

import java.io.Closeable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.Set;
import clojure.lang.Keyword;

// TODO rename to ICruxNode?
/**
 *  Provides API access to Crux.
 */
public interface ICruxAPI extends ICruxIngestAPI, IQueryAPI, IReadDocumentsAPI, Closeable {
    // TODO doc
    public IFixedInstantQueryAPI at();
    public IFixedInstantQueryAPI at(IBitemporalInstant inst);
    public IFixedInstantQueryAPI at(IBitemporalInstant inst, Duration timeout);

    // TODO doc
    public ISnapshot openSnapshotAt();
    public ISnapshot openSnapshotAt(IBitemporalInstant inst);
    public ISnapshot openSnapshotAt(IBitemporalInstant inst, Duration timeout);

    /**
     * Returns a db as of now. Will return the latest consistent
     * snapshot of the db currently known. Does not block.
     *
     * @return the database.
     */
    @Deprecated
    public ICruxDatasource db();

    /**
     * Returns a db as of valid time. Will return the latest
     * consistent snapshot of the db currently known, but does not
     * wait for valid time to be current. Does not block.
     *
     * @param validTime    the valid time.
     * @return             the database.
     */
    @Deprecated
    public ICruxDatasource db(Date validTime);

    /**
     * Returns a db as of valid time and transaction time. Will
     * block until the transaction time is present in the index.
     *
     * @param validTime       the valid time.
     * @param transactionTime the transaction time.
     * @return                the database.
     */
    @Deprecated
    public ICruxDatasource db(Date validTime, Date transactionTime) throws NodeOutOfSyncException;


    /**
     * Returns the transaction history of an entity, in reverse
     * chronological order. Includes corrections, but does not include
     * the actual documents.
     *
     * @param eid an object that can be coerced into an entity id.
     * @return    the transaction history.
     */
    // TODO elaborate about corrections contents
    public List<Map<Keyword,Object>> history(Object eid);

    /**
     * Returns the transaction history of an entity, ordered by valid
     * time / transaction time in chronological order, earliest
     * first. Includes corrections, but does not include the actual
     * documents.
     *
     * Giving null as any of the date arguments makes the range open
     * ended for that value.
     *
     * @param eid                  an object that can be coerced into an entity id.
     * @param validTimeStart       the start valid time or null.
     * @param transactionTimeStart the start transaction time or null.
     * @param validTimeEnd         the end valid time or null, inclusive.
     * @param transactionTimeEnd   the start transaction time or null, inclusive.
     * @return                     the transaction history.
     */
    // TODO elaborate
    public List<Map<Keyword,?>> historyRange(Object eid, Date validTimeStart, Date transactionTimeStart, Date validTimeEnd, Date transactionTimeEnd);

    /**
     * Returns the status of this node as a map.
     *
     * @return the status map.
     */
    public Map<Keyword,?> status();

    /**
     * Checks if a submitted tx was successfully committed.
     *
     * @param submittedTx must be a map returned from {@link #submitTx(List txOps)}.
     * @return true if the submitted transaction was committed, false if it was not committed.
     * @throws NodeOutOfSyncException if the node has not yet indexed the transaction.
     */
    public boolean hasTxCommitted(Map<Keyword,?> submittedTx) throws NodeOutOfSyncException;


    /**
     * Blocks until the node has caught up indexing to the latest tx available
     * at the time this method is called. Will throw an exception on timeout.
     * The returned date is the latest transaction time indexed by this node.
     * This can be used as the second parameter in {@link #db(Date validTime, Date transactionTime)}
     * for consistent reads.
     *
     * @return the latest known transaction time.
     */
    public IBitemporalInstant sync();

    /**
     * Blocks until the node has caught up indexing to the latest tx available
     * at the time this method is called. Will throw an exception on timeout.
     * The returned date is the latest transaction time indexed by this node.
     * This can be used as the second parameter in {@link #db(Date validTime, Date transactionTime)}
     * for consistent reads.
     *
     * @param timeout max time to wait, can be null for the default.
     * @return the latest known transaction time.
     */
    // TODO I'd like to return IBitemporalInstant from here, that'd be a breaking change
    public Date sync(Duration timeout);

    /**
     * @deprecated see {@link #at}
     */
    @Deprecated
    public Date sync(Date txTime, Duration timeout);

    /**
     * Blocks until the node has indexed a transaction that is past the supplied
     * txTime. Will throw on timeout. The returned date is the latest index time
     * when this node has caught up as of this call.
     *
     * @param txTime transaction time to await.
     * @param timeout max time to wait, can be null for the default.
     * @return the latest known transaction time.
     */
    @Deprecated
    public Date awaitTxTime(Date txTime, Duration timeout);

    /**
     * Blocks until the node has indexed a transaction that is at or past the
     * supplied tx. Will throw on timeout. Returns the most recent tx indexed by
     * the node.
     *
     * @param tx Transaction to await, as returned from submitTx.
     * @param timeout max time to wait, can be null for the default.
     * @return the latest known transaction.
     */
    @Deprecated
    public Map<Keyword, ?> awaitTx(Map<Keyword,?> tx, Duration timeout);


    /**
       @return the latest transaction to have been indexed by this node.
     */
    public Map<Keyword, ?> latestCompletedTx();

    /**
       @return the latest transaction to have been submitted to this cluster
    */
    public Map<Keyword, ?> latestSubmittedTx();

    /**
     * Return frequencies of indexed attributes.
     *
     * @return         Map containing attribute freqencies.
     */
    public Map<Keyword, Long> attributeStats();
}
