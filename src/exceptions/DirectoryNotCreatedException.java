package exceptions;

/**
 * Created by Simone on 03/09/2014.
 */
public class DirectoryNotCreatedException  extends LocalException {
    private static final String message = "BassBox local storage has not been created";

    public DirectoryNotCreatedException(){
        super(message);
    }
}