package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class MultipleIdException extends LocalException {
    private static final String message = "Something went wrong: two or more objects has been found with the same id";

    public MultipleIdException(){
        super(message);
    }
}
