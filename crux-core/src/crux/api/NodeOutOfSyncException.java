package crux.api;

import java.util.Date;
import clojure.lang.IExceptionInfo;
import clojure.lang.IPersistentMap;

public class NodeOutOfSyncException extends RuntimeException implements IExceptionInfo {
    private static final long serialVersionUID = 2L;

    private final IPersistentMap data;

    public NodeOutOfSyncException(String message, IPersistentMap data) {
        super(message);
        this.data = data;
    }

    @Override
    public IPersistentMap getData() {
        return data;
    }
}
