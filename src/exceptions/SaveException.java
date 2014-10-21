package exceptions;

/**
 * Created by Simone on 22/07/2014.
 */
public class SaveException extends LocalException {
    private static final String message = "The document has NOT been saved with success";

    public SaveException(){
        super(message);
    }
}