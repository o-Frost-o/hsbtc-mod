package com.hypixel.translation.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

public class HypixelApiCollector {
    private static final String BASE_URL = "https://api.hypixel.net/v2/resources/skyblock/";
    private final HttpClient client;
    private final Path cacheDir;
    private final Gson gson = new Gson();

    public record HypixelItem(
        String id,
        String name,
        String category,
        String tier,
        Map<String, Double> stats
    ) {}

    public record HypixelResult(
        Map<String, HypixelItem> items,
        JsonObject rawSkills,
        JsonObject rawCollections,
        JsonObject rawElection
    ) {}

    public HypixelApiCollector() {
        this(Path.of("cache", "hypixel"));
    }

    public HypixelApiCollector(Path cacheDir) {
        this(cacheDir, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build());
    }

    public HypixelApiCollector(Path cacheDir, HttpClient client) {
        this.cacheDir = cacheDir;
        this.client = client;
    }

    public Optional<JsonArray> fetchItems() {
        JsonObject root = fetchOrCache("items");
        if (root != null && root.has("items") && root.get("items").isJsonArray()) {
            return Optional.of(root.getAsJsonArray("items"));
        }
        return Optional.empty();
    }

    public Optional<JsonObject> fetchSkills() {
        JsonObject root = fetchOrCache("skills");
        if (root != null && root.has("skills") && root.get("skills").isJsonObject()) {
            return Optional.of(root.getAsJsonObject("skills"));
        }
        return Optional.empty();
    }

    public Optional<JsonObject> fetchCollections() {
        JsonObject root = fetchOrCache("collections");
        if (root != null && root.has("collections") && root.get("collections").isJsonObject()) {
            return Optional.of(root.getAsJsonObject("collections"));
        }
        return Optional.empty();
    }

    public Optional<JsonObject> fetchElection() {
        JsonObject root = fetchOrCache("election");
        return root != null ? Optional.of(root) : Optional.empty();
    }

    public HypixelResult collect() {
        JsonObject itemsJson = fetchOrCache("items");
        JsonObject skillsJson = fetchOrCache("skills");
        JsonObject collJson = fetchOrCache("collections");
        JsonObject elecJson = fetchOrCache("election");

        Map<String, HypixelItem> items = new LinkedHashMap<>(6000);
        if (itemsJson != null && itemsJson.has("items") && itemsJson.get("items").isJsonArray()) {
            for (JsonElement el : itemsJson.getAsJsonArray("items")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                String name = o.has("name") ? o.get("name").getAsString() : id;
                String cat = o.has("category") ? o.get("category").getAsString() : "MISC";
                String tier = o.has("tier") ? o.get("tier").getAsString() : "COMMON";
                Map<String, Double> stats = new HashMap<>();
                if (o.has("stats") && o.get("stats").isJsonObject()) {
                    JsonObject sObj = o.getAsJsonObject("stats");
                    for (String k : sObj.keySet()) {
                        try {
                            stats.put(k, sObj.get(k).getAsDouble());
                        } catch (Exception ignored) {}
                    }
                }
                items.put(id, new HypixelItem(id, name, cat, tier, stats));
            }
        }

        return new HypixelResult(
            items,
            skillsJson != null ? skillsJson : new JsonObject(),
            collJson != null ? collJson : new JsonObject(),
            elecJson != null ? elecJson : new JsonObject()
        );
    }

    private JsonObject fetchOrCache(String endpoint) {
        Path cacheFile = cacheDir.resolve(endpoint + ".json");

        // Try online fetch first
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .header("User-Agent", "HypixelSkyblockTranslationMod/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                Files.createDirectories(cacheDir);
                Files.writeString(cacheFile, resp.body());
                return gson.fromJson(resp.body(), JsonObject.class);
            }
        } catch (Exception e) {
            // Network failure or timeout, fallback to cache
        }

        // Cache fallback
        if (Files.exists(cacheFile)) {
            try {
                return gson.fromJson(Files.readString(cacheFile), JsonObject.class);
            } catch (Exception ignored) {}
        }

        return new JsonObject();
    }
}
