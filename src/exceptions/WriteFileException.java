package exceptions;

/**
 * Created by Simone on 10/09/2014.
 */
public class WriteFileException extends LocalException {
    private static final String message = "The file has not been written with success";

    public WriteFileException() {
        super(message);
    }
}
