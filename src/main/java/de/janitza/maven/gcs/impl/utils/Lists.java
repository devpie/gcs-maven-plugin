package de.janitza.maven.gcs.impl.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Jan Müller <jan.mueller@janitza.de>
 * Date: 11.02.15
 * Time: 08:32
 */
public final class Lists {
    private Lists() {
    }

    public static <T> List<T> concat(
            final List<T> list1,
            final List<T> list2
    ) {
        final List<T> newList = new ArrayList<>(list1);
        newList.addAll(list2);
        return newList;
    }

    public static <T> List<T> concat(
            final List<T> list1,
            final T element
    ) {
        return concat(list1, Collections.singletonList(element));
    }

    public static <T> List<T> ensureAList(final List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }
}
