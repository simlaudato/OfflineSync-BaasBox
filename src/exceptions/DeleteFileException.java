package exceptions;

/**
 * Created by Simone on 10/09/2014.
 */
public class DeleteFileException extends LocalException {
    private static final String message = "deletion of pre-existent file failed";

    public DeleteFileException() {
        super(message);
    }
}
