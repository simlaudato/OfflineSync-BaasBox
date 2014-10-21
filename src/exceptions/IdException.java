package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class IdException extends LocalException {
    private static final String message = "The id is wrong";

    public IdException(){
        super(message);
    }
}
