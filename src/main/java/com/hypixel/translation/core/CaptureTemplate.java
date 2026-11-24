package com.hypixel.translation.core;

import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Places named or numbered values anywhere in a localized sentence. */
public final class CaptureTemplate {
    private static final String NAME = "[A-Za-z][A-Za-z0-9]*";
    public static final Pattern NAMED_REFERENCE = Pattern.compile(
            "\\$\\{(" + NAME + ")}|%(" + NAME + "):(\"(?:[^\"\\\\\\r\\n]|\\\\[\"\\\\/bfnrt]|\\\\u[0-9a-fA-F]{4})*\")");
    private static final Pattern REFERENCE = Pattern.compile(NAMED_REFERENCE.pattern() + "|\\$([1-9][0-9]*)");

    public record Span(int sourceStart, int sourceEnd, int outputStart, int outputEnd) {}
    public record Rendered(String text, List<Span> spans) {
        public Rendered(String text) {
            this(text, List.of());
        }
    }

    private CaptureTemplate() {}

    public static String referenceName(Matcher match) {
        return match.group(1) != null ? match.group(1) : match.group(2);
    }

    public static String referenceSample(Matcher match) {
        return match.group(3) == null ? "" : JsonParser.parseString(match.group(3)).getAsString();
    }

    static String bilingualName(String localized, String english) {
        if (localized.equalsIgnoreCase(english) || localized.contains("（" + english + "）")
                || localized.contains("(" + english + ")")) return localized;
        return localized + "（" + english + "）";
    }

    static Rendered bind(String template, Matcher source, UnaryOperator<String> localizeLiteral,
                         BiFunction<String, String, String> localizeCapture) {
        Matcher references = REFERENCE.matcher(template);
        StringBuilder output = new StringBuilder(template.length());
        List<Span> spans = new ArrayList<>();
        int end = 0;
        while (references.find()) {
            String name = referenceName(references);
            Integer group;
            try {
                group = name == null ? Integer.valueOf(references.group(4)) : source.namedGroups().get(name);
            } catch (NumberFormatException invalidIndex) {
                return null;
            }
            if (group == null || group > source.groupCount()) return null;
            output.append(localizeLiteral.apply(template.substring(end, references.start())));
            String value = source.group(group);
            if (value != null) {
                String replacement = localizeCapture.apply(name == null ? references.group(4) : name, value);
                if (replacement == null) return null;
                int outputStart = output.length();
                output.append(replacement);
                if (source.start(group) < source.end(group) && !replacement.isEmpty()) {
                    spans.add(new Span(source.start(group), source.end(group), outputStart, output.length()));
                }
            }
            end = references.end();
        }
        output.append(localizeLiteral.apply(template.substring(end)));
        return new Rendered(output.toString(), List.copyOf(spans));
    }
}
