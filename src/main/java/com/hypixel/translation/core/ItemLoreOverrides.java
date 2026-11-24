package com.hypixel.translation.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Applies the edited parts of an item template to the corresponding live tooltip text. */
public final class ItemLoreOverrides {
    private static final String ENGLISH = "/assets/hsbtr/dictionaries/item_lore_en.json";

    public record Match(int lastLine, List<String> lines) {}
    private record Patch(String english, List<String> localized) {}
    private record Change(int beforeStart, int beforeEnd, int afterStart, int afterEnd) {}

    static final ItemLoreOverrides EMPTY = new ItemLoreOverrides(Map.of(), Map.of());
    private final Map<String, List<Patch>> patches;
    private final Map<String, String> uniqueNames;

    private ItemLoreOverrides(Map<String, List<Patch>> patches, Map<String, String> uniqueNames) {
        this.patches = patches;
        this.uniqueNames = uniqueNames;
    }

    static ItemLoreOverrides compile(Map<String, List<String>> bundled, Map<String, ItemTranslationRecord> items)
            throws IOException {
        var modified = items.values().stream().filter(item -> {
            var original = bundled.get(item.id());
            return original != null && !original.equals(item.lore_template());
        }).toList();
        if (modified.isEmpty()) return EMPTY;

        JsonObject english;
        try (var in = ItemLoreOverrides.class.getResourceAsStream(ENGLISH)) {
            if (in == null) throw new IOException("Missing English item descriptions");
            english = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }

        Map<String, List<Patch>> patches = new HashMap<>();
        for (ItemTranslationRecord item : modified) {
            if (!english.has(item.id())) continue;
            var before = bundled.get(item.id());
            var source = Arrays.asList(english.get(item.id()).getAsString().split("\n", -1));
            boolean aligned = source.size() == before.size();
            for (int i = 0; aligned && i < source.size(); i++) {
                aligned = LoreLineDictionary.normalizeKey(source.get(i)).isBlank()
                        == LoreLineDictionary.normalizeKey(before.get(i)).isBlank();
            }
            List<Patch> changes = aligned
                    ? compilePatches(source, before, item.lore_template())
                    : compileSectionPatches(source, before, item.lore_template());
            if (!changes.isEmpty()) patches.put(item.id(), changes);
        }
        Map<String, String> names = new HashMap<>();
        for (ItemTranslationRecord item : items.values()) {
            if (!item.name_en().isBlank()) names.merge(nameKey(item.name_en()), item.id(), (first, second) -> "");
        }
        names.values().removeIf(id -> !patches.containsKey(id));
        return new ItemLoreOverrides(Map.copyOf(patches), Map.copyOf(names));
    }

    int size() { return patches.size(); }

    boolean hasPatchFor(String id, String name) {
        return id != null ? patches.containsKey(id) : name != null && uniqueNames.containsKey(nameKey(name));
    }

    Map<Integer, Match> match(String id, String name, List<String> tooltip) {
        if (id == null && name != null) id = uniqueNames.get(nameKey(name));
        if (id == null) return Map.of();
        List<Patch> candidates = patches.get(id);
        if (candidates == null || tooltip.size() < 2) return Map.of();

        Map<Integer, Match> matched = new HashMap<>();
        Set<Patch> used = new HashSet<>();
        List<String> clean = tooltip.stream().map(LoreLineDictionary::normalizeKey).toList();
        for (int first = 1; first < clean.size(); first++) {
            if (clean.get(first).isBlank()) continue;
            for (Patch patch : candidates) {
                if (used.contains(patch) || !patch.english().startsWith(clean.get(first))) continue;
                StringBuilder text = new StringBuilder(clean.get(first));
                for (int last = first; last < clean.size(); last++) {
                    if (last > first && !clean.get(last).isBlank()) text.append(' ').append(clean.get(last));
                    if (!patch.english().startsWith(text.toString())) break;
                    if (!patch.english().contentEquals(text)) continue;
                    matched.put(first, new Match(last, patch.localized()));
                    used.add(patch);
                    first = last;
                    break;
                }
                if (used.contains(patch)) break;
            }
        }
        return matched;
    }

    private static List<Patch> compileSectionPatches(List<String> source, List<String> before, List<String> after) {
        List<List<String>> sourceSections = splitSections(source);
        List<List<String>> beforeSections = splitSections(before);
        if (sourceSections.size() != beforeSections.size()) {
            String full = LoreLineDictionary.normalizeKey(String.join(" ", source));
            return full.isBlank() ? List.of() : List.of(new Patch(full, List.copyOf(after)));
        }
        List<List<String>> afterSections = splitSections(after);
        List<String> beforeKeys = beforeSections.stream().map(sec -> String.join("\n", sec)).toList();
        List<String> afterKeys = afterSections.stream().map(sec -> String.join("\n", sec)).toList();
        List<Patch> changes = new ArrayList<>();
        for (Change region : changedRegions(beforeKeys, afterKeys)) {
            List<String> enLines = new ArrayList<>();
            for (int k = region.beforeStart(); k < region.beforeEnd(); k++) {
                enLines.addAll(sourceSections.get(k));
            }
            String sample = LoreLineDictionary.normalizeKey(String.join(" ", enLines));
            if (sample.isBlank()) continue;
            List<String> localized = new ArrayList<>();
            for (int k = region.afterStart(); k < region.afterEnd(); k++) {
                if (k > region.afterStart()) localized.add("");
                localized.addAll(afterSections.get(k));
            }
            changes.add(new Patch(sample, List.copyOf(localized)));
        }
        changes.sort(Comparator.comparingInt((Patch patch) -> patch.english().length()).reversed());
        return List.copyOf(changes);
    }

    private static List<List<String>> splitSections(List<String> lines) {
        List<List<String>> sections = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (LoreLineDictionary.normalizeKey(line).isBlank()) {
                if (!current.isEmpty()) {
                    sections.add(List.copyOf(current));
                    current.clear();
                }
            } else {
                current.add(line);
            }
        }
        if (!current.isEmpty()) sections.add(List.copyOf(current));
        return sections;
    }

    private static List<Patch> compilePatches(List<String> source, List<String> before, List<String> after) {
        List<Change> regions = changedRegions(before, after);
        List<Patch> changes = new ArrayList<>();
        for (Change region : regions) {
            String sample = LoreLineDictionary.normalizeKey(
                    String.join(" ", source.subList(region.beforeStart(), region.beforeEnd())));
            if (!sample.isBlank()) {
                changes.add(new Patch(sample, List.copyOf(after.subList(region.afterStart(), region.afterEnd()))));
            }
        }
        changes.sort(Comparator.comparingInt((Patch patch) -> patch.english().length()).reversed());
        return List.copyOf(changes);
    }

    private static List<Change> changedRegions(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        // Match unchanged lines so edits can reflow a paragraph without shifting the rest of the tooltip.
        int[][] common = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                common[i][j] = before.get(i).equals(after.get(j))
                        ? 1 + common[i + 1][j + 1]
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }
        List<Change> changes = new ArrayList<>();
        int i = 0, j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && before.get(i).equals(after.get(j))) {
                i++;
                j++;
                continue;
            }
            int startBefore = i;
            int startAfter = j;
            while ((i < n || j < m) && !(i < n && j < m && before.get(i).equals(after.get(j)))) {
                if (i < n && (j == m || common[i + 1][j] >= common[i][j + 1])) i++;
                else j++;
            }
            if (startBefore == i) {
                // An insertion alone needs an unchanged English line as its anchor.
                if (i < n) {
                    do {
                        i++;
                        j++;
                    } while (i < n && j < m && before.get(i - 1).isBlank() && before.get(i).equals(after.get(j)));
                } else if (startBefore > 0 && startAfter > 0) {
                    startBefore--;
                    startAfter--;
                }
            }
            changes.add(new Change(startBefore, i, startAfter, j));
        }
        return changes;
    }

    private static String nameKey(String name) {
        return LoreLineDictionary.normalizeKey(name).toLowerCase(Locale.ROOT);
    }
}
