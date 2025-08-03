package com.bloom.app.domain.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public static String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return FORMATTER.format(instant);
    }
}