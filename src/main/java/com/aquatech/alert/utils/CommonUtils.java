package com.aquatech.alert.utils;

import java.util.Collection;

public class CommonUtils {
    public static boolean isEmptyCollection(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static boolean isEmptyString(String str) {
        return str == null || str.trim().isEmpty();
    }
}
