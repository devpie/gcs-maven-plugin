package de.janitza.maven.gcs.api.exception;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 13:13
 */
public class BuildException extends Exception {

    private static final long serialVersionUID = -3027004012737087503L;

    public BuildException(final String message) {
        super(message);
    }

    public BuildException(
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
    }

    public BuildException(final Throwable cause) {
        super(cause);
    }
}
