package com.hypixel.translation.core;

import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** Dynamic rules authored against a concrete sample in translation review. */
final class ReviewedPatterns {
    static final String SECTION = "review_patterns";

    private record Rule(Pattern regex, String template, Map<String, Map<String, String>> terms,
                        Map<String, String> types) {
        String localize(String role, String value, BiFunction<String, String, String> resolveEntity) {
            Map<String, String> choices = role == null ? null : terms.get(role);
            String type = role == null ? null : types.get(role);
            String localized = choices == null ? null : choices.get(value);
            if (localized == null) return type == null ? value : resolveEntity.apply(type, value);
            return "npc".equals(type) ? CaptureTemplate.bilingualName(localized, value) : localized;
        }
    }

    private volatile Map<String, Rule> rules = Map.of();

    void load(JsonObject root, String section) {
        if (!root.has(SECTION)) return;
        JsonObject scopes = root.getAsJsonObject(SECTION);
        if (!scopes.has(section)) return;
        Map<String, Rule> loaded = new LinkedHashMap<>(rules);
        scopes.getAsJsonObject(section).entrySet().forEach(entry -> {
            JsonObject json = entry.getValue().getAsJsonObject();
            Map<String, Map<String, String>> terms = new LinkedHashMap<>();
            if (json.has("terms")) {
                json.getAsJsonObject("terms").entrySet().forEach(role -> {
                    Map<String, String> choices = new LinkedHashMap<>();
                    role.getValue().getAsJsonObject().entrySet().forEach(choice ->
                            choices.put(choice.getKey(), choice.getValue().getAsString()));
                    terms.put(role.getKey(), choices);
                });
            }
            loaded.put(section + "\n" + entry.getKey(), new Rule(Pattern.compile(json.get("regex").getAsString()),
                    json.get("zh_tw").getAsString(), terms, captureTypes(json)));
        });
        rules = Collections.unmodifiableMap(loaded);
    }

    boolean isEmpty() { return rules.isEmpty(); }

    void clear() { rules = Map.of(); }

    void mergeFrom(ReviewedPatterns src) {
        if (src == null || src.rules.isEmpty()) return;
        if (rules.isEmpty()) {
            rules = src.rules;
            return;
        }
        Map<String, Rule> merged = new LinkedHashMap<>(rules);
        merged.putAll(src.rules);
        rules = Collections.unmodifiableMap(merged);
    }

    static Map<String, String> captureTypes(JsonObject rule) {
        if (!rule.has("capture_types")) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        rule.getAsJsonObject("capture_types").entrySet()
                .forEach(type -> result.put(type.getKey(), type.getValue().getAsString()));
        return Map.copyOf(result);
    }

    CaptureTemplate.Rendered match(String source, UnaryOperator<String> localizeLiteral,
                                  BiFunction<String, String, String> resolveEntity) {
        for (Rule rule : rules.values()) {
            var match = rule.regex().matcher(source);
            if (!match.matches()) continue;
            var rendered = CaptureTemplate.bind(rule.template(), match, localizeLiteral,
                    (role, value) -> rule.localize(role, value, resolveEntity));
            if (rendered != null) return rendered;
        }
        return null;
    }
}
