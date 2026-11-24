package com.hypixel.translation.pipeline;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class NeuRepoCollector {
    public static final Path DEFAULT_PRISM_REPO_PATH = Path.of(
        System.getProperty("user.home"),
        "AppData", "Roaming", "PrismLauncher", "instances", "26.2", "minecraft", "config", "skyblocker", "item-repo"
    );

    private final Path repoPath;
    private final Gson gson = new Gson();

    public record NeuItem(
        String internalname,
        String itemid,
        String displayname,
        List<String> lore,
        String nbttag,
        int damage,
        String clickcommand,
        String crafttext,
        List<String> info
    ) {}

    public record NeuReforge(
        String reforgeName,
        String itemTypes,
        List<String> requiredRarities,
        Map<String, Map<String, Double>> reforgeStats
    ) {}

    public record NeuReforgeStone(
        String internalName,
        String reforgeName,
        String itemTypes,
        List<String> requiredRarities,
        Map<String, Integer> reforgeCosts
    ) {}

    public record NeuRepoResult(
        Map<String, NeuItem> items,
        Map<String, NeuReforge> reforges,
        Map<String, NeuReforgeStone> reforgeStones,
        Set<String> collisionBaseNames,
        JsonObject pets,
        JsonObject petNums,
        JsonObject enchants
    ) {}

    public NeuRepoCollector() {
        this(DEFAULT_PRISM_REPO_PATH);
    }

    public NeuRepoCollector(Path repoPath) {
        this.repoPath = repoPath;
    }

    public NeuRepoResult collect() throws IOException {
        Map<String, NeuItem> items = loadNeuItems(repoPath);

        // Load reforges (blacksmith)
        Map<String, NeuReforge> reforges = loadReforges(repoPath);

        // Load reforge stones (advanced)
        Map<String, NeuReforgeStone> reforgeStones = loadReforgeStones(repoPath);

        // Compute 276 collision base names
        Set<String> collisions = computeCollisions(items, reforges, reforgeStones);

        // Load other constants
        JsonObject pets = loadConstants(repoPath, "pets.json");
        JsonObject petNums = loadConstants(repoPath, "petnums.json");
        JsonObject enchants = loadConstants(repoPath, "enchants.json");

        return new NeuRepoResult(items, reforges, reforgeStones, collisions, pets, petNums, enchants);
    }

    public Map<String, NeuItem> loadNeuItems(Path path) {
        Map<String, NeuItem> items = new ConcurrentHashMap<>(10000);
        Path itemsDir = path.resolve("items");
        if (!Files.exists(itemsDir)) {
            return items;
        }

        try (Stream<Path> stream = Files.list(itemsDir)) {
            stream.parallel()
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try (Reader reader = Files.newBufferedReader(p)) {
                        JsonObject obj = gson.fromJson(reader, JsonObject.class);
                        if (obj == null) return;

                        String internalName = obj.has("internalname")
                            ? obj.get("internalname").getAsString()
                            : p.getFileName().toString().replace(".json", "");
                        String itemid = obj.has("itemid") ? obj.get("itemid").getAsString() : "";
                        String displayname = obj.has("displayname") ? obj.get("displayname").getAsString() : "";
                        String nbttag = obj.has("nbttag") ? obj.get("nbttag").getAsString() : "";
                        int damage = obj.has("damage") ? obj.get("damage").getAsInt() : 0;
                        String clickcommand = obj.has("clickcommand") ? obj.get("clickcommand").getAsString() : "";
                        String crafttext = obj.has("crafttext") ? obj.get("crafttext").getAsString() : "";

                        List<String> lore = new ArrayList<>();
                        if (obj.has("lore") && obj.get("lore").isJsonArray()) {
                            for (JsonElement e : obj.getAsJsonArray("lore")) {
                                lore.add(e.getAsString());
                            }
                        }

                        List<String> info = new ArrayList<>();
                        if (obj.has("info") && obj.get("info").isJsonArray()) {
                            for (JsonElement e : obj.getAsJsonArray("info")) {
                                info.add(e.getAsString());
                            }
                        }

                        items.put(internalName, new NeuItem(
                            internalName, itemid, displayname, lore, nbttag, damage, clickcommand, crafttext, info
                        ));
                    } catch (Exception ignored) {}
                });
        } catch (IOException e) {
            System.err.println("[NeuRepoCollector] Error scanning items directory: " + e.getMessage());
        }

        return items;
    }

    public Map<String, JsonObject> loadItemsAsJsonObjects(Path path) {
        Map<String, JsonObject> items = new ConcurrentHashMap<>(10000);
        Path itemsDir = path.resolve("items");
        if (!Files.exists(itemsDir)) return items;

        try (Stream<Path> stream = Files.list(itemsDir)) {
            stream.parallel()
                .filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try (Reader r = Files.newBufferedReader(p)) {
                        JsonObject json = gson.fromJson(r, JsonObject.class);
                        if (json != null) {
                            String internalName = json.has("internalname")
                                ? json.get("internalname").getAsString()
                                : p.getFileName().toString().replace(".json", "");
                            items.put(internalName, json);
                        }
                    } catch (Exception ignored) {}
                });
        } catch (IOException ignored) {}
        return items;
    }

    public Map<String, NeuReforge> loadReforges(Path path) {
        Map<String, NeuReforge> map = new LinkedHashMap<>();
        Path reforgesFile = path.resolve("constants").resolve("reforges.json");
        if (!Files.exists(reforgesFile)) return map;

        try (Reader r = Files.newBufferedReader(reforgesFile)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root != null) {
                for (String key : root.keySet()) {
                    try {
                        JsonElement el = root.get(key);
                        if (!el.isJsonObject()) continue;
                        JsonObject refObj = el.getAsJsonObject();
                        String name = refObj.has("reforgeName") ? refObj.get("reforgeName").getAsString() : key;
                        String types = "";
                        if (refObj.has("itemTypes")) {
                            JsonElement tEl = refObj.get("itemTypes");
                            types = tEl.isJsonPrimitive() ? tEl.getAsString() : tEl.toString();
                        }
                        map.put(name, new NeuReforge(name, types, List.of(), Map.of()));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    public Map<String, NeuReforgeStone> loadReforgeStones(Path path) {
        Map<String, NeuReforgeStone> map = new LinkedHashMap<>();
        Path stonesFile = path.resolve("constants").resolve("reforgestones.json");
        if (!Files.exists(stonesFile)) return map;

        try (Reader r = Files.newBufferedReader(stonesFile)) {
            JsonObject root = gson.fromJson(r, JsonObject.class);
            if (root != null) {
                for (String key : root.keySet()) {
                    try {
                        JsonElement el = root.get(key);
                        if (!el.isJsonObject()) continue;
                        JsonObject stObj = el.getAsJsonObject();
                        String refName = stObj.has("reforgeName") ? stObj.get("reforgeName").getAsString() : key;
                        String types = "";
                        if (stObj.has("itemTypes")) {
                            JsonElement tEl = stObj.get("itemTypes");
                            types = tEl.isJsonPrimitive() ? tEl.getAsString() : tEl.toString();
                        }
                        map.put(key, new NeuReforgeStone(key, refName, types, List.of(), Map.of()));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return map;
    }

    public Set<String> computeCollisions(
        Map<String, NeuItem> items,
        Map<String, NeuReforge> reforges,
        Map<String, NeuReforgeStone> reforgeStones
    ) {
        Set<String> allReforgeNames = new HashSet<>(reforges.keySet());
        for (NeuReforgeStone stone : reforgeStones.values()) {
            allReforgeNames.add(stone.reforgeName());
        }

        Set<String> collisions = new TreeSet<>();
        for (NeuItem it : items.values()) {
            String cleanName = it.displayname().replaceAll("§[0-9a-fklmnor]", "").trim();
            for (String ref : allReforgeNames) {
                if (cleanName.startsWith(ref + " ")) {
                    collisions.add(cleanName);
                    break;
                }
            }
        }
        return collisions;
    }

    public JsonObject loadConstants(Path path, String filename) {
        Path constFile = path.resolve("constants").resolve(filename);
        if (!Files.exists(constFile)) return new JsonObject();
        try (Reader r = Files.newBufferedReader(constFile)) {
            JsonObject obj = gson.fromJson(r, JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
