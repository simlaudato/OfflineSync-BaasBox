package exceptions;

/**
 * Created by Simone on 25/07/2014.
 */
public class DirectoryException  extends LocalException {
    private static final String message = "BassBox local storage is empty or doesn't exist";

    public DirectoryException(){
        super(message);
    }
}
