package de.janitza.maven.gcs.impl.utils;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 12.02.15
 * Time: 10:50
 */
public final class Strings {
    private Strings() {
    }

    public static boolean isEmpty(final String string) {
        return string == null || string.isEmpty();
    }
}
