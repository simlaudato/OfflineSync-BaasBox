package exceptions;

/**
 * Created by Simone on 04/09/2014.
 */
public class SyncException extends Exception {
    public SyncException() {
        super();
    }

    public SyncException(String detailMessage) {
        super(detailMessage);
    }

    public SyncException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public SyncException(Throwable throwable) {
        super(throwable);
    }
}
