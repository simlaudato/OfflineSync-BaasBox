package exceptions;

/**
 * Created by Simone on 21/07/2014.
 */
public class CollectionException extends LocalException{
    private static final String message = "The collection name is wrong";

    public CollectionException(){
        super(message);
    }
}
