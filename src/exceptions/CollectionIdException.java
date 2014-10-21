package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class CollectionIdException extends LocalException {
    private static final String message = "The id or collection doesn't match with an existing id or collection in the Storage or it is null";

    public CollectionIdException(){
        super(message);
    }
}
