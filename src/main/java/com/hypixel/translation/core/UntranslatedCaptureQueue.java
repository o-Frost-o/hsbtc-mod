package com.hypixel.translation.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.translation.config.ComponentCategory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe bounded capture queue for collecting untranslated gameplay text.
 * Uses SHA-256 hashing for deduplication on color-stripped clean text.
 */
public class UntranslatedCaptureQueue {
    public static final int DEFAULT_CAPACITY = 5000;

    private final int maxCapacity;
    private final Map<String, UntranslatedRecord> queue = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public UntranslatedCaptureQueue() {
        this(DEFAULT_CAPACITY);
    }

    public UntranslatedCaptureQueue(int maxCapacity) {
        this.maxCapacity = Math.max(1, maxCapacity);
    }

    public void capture(String rawText, ComponentCategory category) {
        capture(rawText, category != null ? category.name() : "GENERAL");
    }

    public void capture(String rawText, String categoryTag) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return;
        }

        String clean = rawText.replaceAll("§[0-9a-fk-or]", "").trim();
        if (clean.isEmpty()) {
            return;
        }

        String hash = sha256(clean);
        if (queue.size() >= maxCapacity && !queue.containsKey(hash)) {
            return; // Gracefully drop excess entries to avoid memory leaks
        }

        String tag = (categoryTag != null && !categoryTag.isBlank()) ? categoryTag : "GENERAL";
        long now = System.currentTimeMillis();

        queue.compute(hash, (k, old) -> {
            int count = old != null ? old.seenCount() + 1 : 1;
            return new UntranslatedRecord(hash, clean, rawText, tag, now, count);
        });
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    public Map<String, UntranslatedRecord> getRecords() {
        return Collections.unmodifiableMap(queue);
    }

    public void flushToJson(Path path) throws IOException {
        JsonArray arr = new JsonArray();
        for (UntranslatedRecord r : queue.values()) {
            JsonObject o = new JsonObject();
            o.addProperty("hash", r.hash());
            o.addProperty("cleanText", r.cleanText());
            o.addProperty("rawText", r.rawText());
            o.addProperty("category", r.category());
            o.addProperty("timestamp", r.timestamp());
            o.addProperty("seenCount", r.seenCount());
            arr.add(o);
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, gson.toJson(arr), StandardCharsets.UTF_8);
    }

    public int loadFromJson(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return 0;
        }
        String content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.isBlank()) {
            return 0;
        }
        JsonElement parsed = JsonParser.parseString(content);
        if (!parsed.isJsonArray()) {
            return 0;
        }
        int loaded = 0;
        for (JsonElement el : parsed.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String rawText = o.has("rawText") ? o.get("rawText").getAsString()
                    : (o.has("cleanText") ? o.get("cleanText").getAsString() : "");
            if (rawText.isBlank()) continue;
            String clean = rawText.replaceAll("§[0-9a-fk-or]", "").trim();
            if (clean.isEmpty()) continue;

            String hash = sha256(clean);
            if (queue.size() >= maxCapacity && !queue.containsKey(hash)) {
                break;
            }
            String category = o.has("category") ? o.get("category").getAsString() : "GENERAL";
            long timestamp = o.has("timestamp") ? o.get("timestamp").getAsLong() : System.currentTimeMillis();
            int seenCount = o.has("seenCount") ? Math.max(1, o.get("seenCount").getAsInt()) : 1;

            queue.compute(hash, (k, old) -> {
                if (old == null) {
                    return new UntranslatedRecord(hash, clean, rawText, category, timestamp, seenCount);
                }
                return new UntranslatedRecord(hash, clean, old.rawText(), old.category(),
                        Math.max(old.timestamp(), timestamp), old.seenCount() + seenCount);
            });
            loaded++;
        }
        return loaded;
    }

    private String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
