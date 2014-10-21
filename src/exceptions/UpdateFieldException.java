package exceptions;

/**
 * Created by Simone on 22/07/2014.
 */
public class UpdateFieldException extends LocalException {
    private static final String message = "You are trying to update a particular field not accessible";

    public UpdateFieldException(){
        super(message);
    }
}
