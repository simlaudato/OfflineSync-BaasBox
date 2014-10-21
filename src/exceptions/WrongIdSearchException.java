package exceptions;

/**
 * Created by Simone on 22/07/2014.
 */

public class WrongIdSearchException extends LocalException {
    private static final String message = "The id starts with 'L', you are searching for a Document by its Remote ID";

    public WrongIdSearchException() {
        super(message);
    }
}