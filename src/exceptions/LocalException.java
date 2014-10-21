package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class LocalException extends Exception {
    public LocalException() {
    }

    public LocalException(String detailMessage) {
        super(detailMessage);
    }

    public LocalException(Throwable throwable) {
        super(throwable);
    }

    public LocalException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }
}
