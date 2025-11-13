package com.bloom.app.domain.enums;

import lombok.Getter;

@Getter
public enum RomanMonth {
    JANUARY(1, "I"),
    FEBRUARY(2, "II"),
    MARCH(3, "III"),
    APRIL(4, "IV"),
    MAY(5, "V"),
    JUNE(6, "VI"),
    JULY(7, "VII"),
    AUGUST(8, "VIII"),
    SEPTEMBER(9, "IX"),
    OCTOBER(10, "X"),
    NOVEMBER(11, "XI"),
    DECEMBER(12, "XII");

    private final int monthNumber;
    private final String roman;

    RomanMonth(int monthNumber, String roman) {
        this.monthNumber = monthNumber;
        this.roman = roman;
    }

    public static String fromNumber(int monthNumber) {
        for (RomanMonth m : values()) {
            if (m.getMonthNumber() == monthNumber) return m.getRoman();
        }
        throw new IllegalArgumentException("Invalid month: " + monthNumber);
    }
}

