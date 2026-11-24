package com.hypixel.translation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Translates visible menu text without changing server inventory data. */
public final class MenuDictionary {
    private static final Pattern COLOR = Pattern.compile("§[0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Map<String, MenuDictionary> BUNDLED_CACHE = new ConcurrentHashMap<>();
    private final DialogueDictionary titles = new DialogueDictionary();
    private final DialogueDictionary labels = new DialogueDictionary();
    private final DialogueDictionary people = new DialogueDictionary();
    private final Set<String> localizedLabels = new HashSet<>();

    void setCaptureLocalizer(BiFunction<String, String, String> localizer) {
        titles.setCaptureLocalizer(localizer);
        labels.setCaptureLocalizer(localizer);
    }

    public void loadFromResource(String resourcePath) throws IOException {
        if (BundledResourceCache.isBundledPath(resourcePath)) {
            MenuDictionary snapshot = BundledResourceCache.getOrLoad(BUNDLED_CACHE, resourcePath, () -> {
                MenuDictionary loaded = new MenuDictionary();
                loaded.loadFromJson(BundledResourceCache.readResourceUtf8(MenuDictionary.class, resourcePath));
                return loaded;
            });
            copyFrom(snapshot);
            return;
        }
        loadFromJson(BundledResourceCache.readResourceUtf8(getClass(), resourcePath));
    }

    private void copyFrom(MenuDictionary src) {
        if (src == null) return;
        titles.copyFrom(src.titles);
        labels.copyFrom(src.labels);
        people.copyFrom(src.people);
        localizedLabels.addAll(src.localizedLabels);
    }

    public void loadFromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        loadNames(root, "titles", titles);
        loadNames(root, "lines", labels);
        loadNames(root, "people", people);
        titles.loadReviewed(root, "titles");
        labels.loadReviewed(root, "lines");
        if (root.has("patterns") && root.get("patterns").isJsonArray()) {
            labels.loadPatterns(root.getAsJsonArray("patterns"));
        }
    }

    private void loadNames(JsonObject root, String field, DialogueDictionary dictionary) {
        if (!root.has(field)) return;
        root.getAsJsonObject(field).entrySet().forEach(entry -> {
            String value = entry.getValue().getAsString();
            dictionary.registerDialogue(entry.getKey(), value);
            localizedLabels.add(COLOR.matcher(value).replaceAll("").strip());
        });
    }

    public String translate(String line, boolean title) {
        if (line == null || line.isBlank()) return line;
        if (title) {
            String heading = titles.translate(line);
            if (!heading.equals(line)) return heading;
            String person = translatePerson(line);
            if (!person.equals(line)) return person;
        }
        return labels.translate(line);
    }

    public boolean isLocalized(String line) {
        return localizedLabels.contains(COLOR.matcher(line).replaceAll("").strip());
    }

    public boolean recognizesTitle(String line) {
        return line != null && (!titles.translate(line).equals(line) || !translatePerson(line).equals(line));
    }

    public String translatePerson(String line) {
        if (line == null || line.indexOf(':') >= 0) return line;
        return people.translate(line);
    }

    public boolean hasEnglishReference(String line) {
        int open = line.lastIndexOf('（');
        if (open < 0) return false;
        int close = line.indexOf('）', open + 1);
        if (close < 0) return false;
        String english = COLOR.matcher(line.substring(open + 1, close)).replaceAll("");
        return !people.translate(english).equals(english);
    }
}
