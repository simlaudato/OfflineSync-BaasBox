package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class LocalIDException extends LocalException {
    private static final String message = "The id is not a valid localID value, it doesn't start with 'L'";

    public LocalIDException(){
        super(message);
    }
}
