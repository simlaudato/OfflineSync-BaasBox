package exceptions;

/**
 * Created by Simone on 22/07/2014.
 */
public class CreationFieldException extends LocalException {
    private static final String message = "The given JsonObject contains reserved fields that start with '_', '@', 'id', 'localID' ";

    public CreationFieldException(){
        super(message);
    }
}
