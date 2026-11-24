package com.hypixel.translation.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility parser for Roman numerals (I-X) with word-boundary safety to prevent
 * false-positive matching on English words (e.g. "Bonsai").
 */
public class RomanNumeralParser {
    private static final String[] ROMANS = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final String[] CHINESE = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};
    private static final Pattern ROMAN_WORD_PATTERN = Pattern.compile("(?<![A-Za-z0-9-])(I|II|III|IV|V|VI|VII|VIII|IX|X)(?![A-Za-z0-9-])");

    public RomanNumeralParser() {}

    public int toArabic(String roman) {
        if (roman == null) return -1;
        for (int i = 1; i <= 10; i++) {
            if (ROMANS[i].equals(roman)) return i;
        }
        return -1;
    }

    public String toRoman(int val) {
        if (val >= 1 && val <= 10) return ROMANS[val];
        return String.valueOf(val);
    }

    public String toChinese(int val) {
        if (val >= 1 && val <= 10) return CHINESE[val];
        return String.valueOf(val);
    }

    public String translateRomanLine(String line, boolean convertToChinese) {
        if (line == null) return null;
        Matcher m = ROMAN_WORD_PATTERN.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int val = toArabic(m.group(1));
            String rep = convertToChinese ? toChinese(val) : m.group(1);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
