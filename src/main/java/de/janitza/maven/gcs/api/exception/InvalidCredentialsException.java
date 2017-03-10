package de.janitza.maven.gcs.api.exception;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 10.02.15
 * Time: 14:03
 */
public class InvalidCredentialsException extends Exception {
    public InvalidCredentialsException(final String message) {
        super(message);
    }

    public InvalidCredentialsException(
            final String message,
            final Throwable cause
    ) {
        super(message, cause);
    }

    public InvalidCredentialsException(final Throwable cause) {
        super(cause);
    }

    public InvalidCredentialsException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace
    ) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
