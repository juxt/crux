package crux.api.alpha;

import clojure.lang.Keyword;

import java.util.Date;
import java.util.Map;

import static crux.api.alpha.Util.keyword;

public class TxResult {
    private static final Keyword TX_TIME = keyword("crux.tx/tx-time");
    private static final Keyword TX_ID = keyword("crux.tx/tx-id");

    public final Date txTime;
    public final long txId;
    public Map<Keyword, ?> txResultMap;

    private TxResult(Map<Keyword, ?> txResultMap, Date txTime, long txId) {
        this.txTime = txTime;
        this.txId = txId;
    }

    public static TxResult txResult(Map<Keyword, ?> txResultMap) {
        Date txTime = (Date) txResultMap.get(TX_TIME);
        long txId = (Long) txResultMap.get(TX_ID);
        return new TxResult(txResultMap, txTime, txId);
    }

    public Map<Keyword,?> toEdn() {
        return txResultMap;
    }
}
