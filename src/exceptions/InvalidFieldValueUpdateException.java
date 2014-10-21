package exceptions;

/**
 * Created by Simone on 22/07/2014.
 */
public class InvalidFieldValueUpdateException extends LocalException{

    private static final String message = "The value inserted for update is not and instance of String, Long, Double, Boolean, JsonObject, JsonArray, JsonStructure or byte[]";

    public InvalidFieldValueUpdateException(){
        super(message);
    }
}
